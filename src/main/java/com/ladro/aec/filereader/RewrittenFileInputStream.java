package com.ladro.aec.filereader;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RewrittenFileInputStream implements Runnable, Closeable {

	private final File file;

	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	private final IMessageSender sender;

	private long timestamp;

	public RewrittenFileInputStream(File inputFile, IMessageSender sender) {
		this.file = inputFile;
		this.sender = sender;
		this.timestamp = -1;
		System.err.println("Looking for file " + inputFile);
	}

	@Override
	public void run() {
		System.err.println("Starting read thread for file " + file);
		while (true) {
			if (!file.exists()) {
				if (!doSleep(1000)) {
					System.err.println("Interrupted");
					break;
				}
				continue;
			}
			if (timestamp == file.lastModified()) {
				if (!doSleep(500)) {
					System.err.println("Interrupted");
					break;
				}				
				continue;
			}
			int linesPosted = doFileRead();
			timestamp = file.lastModified();
			System.err.println("Read " + linesPosted + " lines");
		}
		System.err.println("Done with thread");
	}

	private int doFileRead() {
		System.err.println("Read file " + file);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			String line;
			List<String> lines = new ArrayList<String>();
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			sender.sendMessage(lines.toArray(new String[lines.size()]));
			return lines.size();
		} catch (IOException ex) {
			ex.printStackTrace();
			return 0;
		} finally {
			if (reader != null)
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}

	private boolean doSleep(long millis) {
		try {
			if (shutdownLatch.await(millis, TimeUnit.MILLISECONDS)) {
				System.err.println("Told to shutdown");
				return false;
			}
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public void close() throws IOException {
		System.err.println("Shutting down");
		shutdownLatch.countDown();
	}

}
