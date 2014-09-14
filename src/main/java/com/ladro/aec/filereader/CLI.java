package com.ladro.aec.filereader;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLI {

	private static final String PROD_SERVER_URL = "http://aec.ladro.com/api";
	private static final String LOCAL_SERVER_URL = "http://localhost:5000/api";
	private static final String DEFAULT_STOP_FILE = "stop.txt";
	private static final int FILE_SLEEP_TIME = 250;

	public static void tailReader(IMessageSender messageSender, File inputFile) throws IOException {
		TailInputStream tailIn = new TailInputStream(inputFile);
		InputStreamReader readerIn = new InputStreamReader(tailIn);
		BufferedReader in = new BufferedReader(readerIn);
		String line = null;
		while ((line = in.readLine()) != null) {
			messageSender.sendMessage(line);
		}
		in.close();
	}

	private static class StopFileWatcher implements Runnable, Closeable {

		private final File m_stopFile;
		private final List<Closeable> m_closeableReaders;
		private final CountDownLatch m_shutdownLatch = new CountDownLatch(1);

		public StopFileWatcher(File stopFile, List<Closeable> closeableReaders) {
			m_stopFile = stopFile;
			m_closeableReaders = closeableReaders;
		}

		@Override
		public void run() {
			while (true) {
				if (m_stopFile.isFile()) {
					break;
				}
				try {
					if (m_shutdownLatch.await(2000, TimeUnit.MILLISECONDS)) {
						break;
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
					break;
				}
			}
			for (Closeable me : m_closeableReaders) {
				try {
					me.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void close() throws IOException {
			m_shutdownLatch.countDown();
		}

	}

	public static void newFileReader(final RemoteMessageSender messageSender, File stopFile, Map<String, File> inputFiles) throws IOException,
			InterruptedException {
		ExecutorService execService = Executors.newFixedThreadPool(1 + inputFiles.size());
		List<Closeable> closeableReaders = new ArrayList<Closeable>();
		for (Entry<String, File> me : inputFiles.entrySet()) {
			final String channel = me.getKey();
			RewrittenFileInputStream reader = new RewrittenFileInputStream(me.getValue(), new IMessageSender() {

				@Override
				public void sendMessage(String... lines) {
					System.err.println("Starting channel " + channel + " send of " + lines.length + " lines");
					messageSender.sendMessage(channel, lines);
					System.err.println("Done send");
				}
			});
			execService.submit(reader);
			closeableReaders.add(reader);
		}
		execService.submit(new StopFileWatcher(stopFile, closeableReaders));
		execService.shutdown();
		System.err.println("Waiting for shutdown notice");
		execService.awaitTermination(1000, TimeUnit.DAYS);
		System.err.println("Shutdown");
	}

	public static void newFileReader2(final RemoteMessageSender messageSender, File stopFile, Map<String, File> inputFiles) throws IOException,
			InterruptedException {
		ExecutorService execService = Executors.newFixedThreadPool(1 + inputFiles.size());
		List<Closeable> closeableReaders = new ArrayList<Closeable>();
		for (Entry<String, File> me : inputFiles.entrySet()) {
			FileTimeStampWatcher watcher = new FileTimeStampWatcher(me.getValue(), me.getKey(), messageSender);			
			execService.submit(watcher);
			closeableReaders.add(watcher);
		}
		execService.submit(new StopFileWatcher(stopFile, closeableReaders));
		execService.shutdown();
		System.err.println("Waiting for shutdown notice");
		execService.awaitTermination(1000, TimeUnit.DAYS);
		System.err.println("Shutdown");
	}

	private static class FileTimeStampWatcher implements Runnable, Closeable {

		private final File m_file;
		private final CountDownLatch m_shutdownLatch = new CountDownLatch(1);
		private long m_lastReadTime = -1;
		private final String m_channel;
		private final RemoteMessageSender m_messageSender;

		public FileTimeStampWatcher(File file, String channel, RemoteMessageSender messageSender) {
			m_file = file;
			m_channel = channel;
			m_messageSender = messageSender;
		}

		@Override
		public void run() {
			while (true) {
				if (!m_file.exists() || m_file.lastModified() == m_lastReadTime) {
					try {
						if (m_shutdownLatch.await(FILE_SLEEP_TIME, TimeUnit.MILLISECONDS)) {
							break;
						}
					} catch (InterruptedException e) {
						break;
					}
					continue;
				}
				m_messageSender.sendMessage(m_channel, m_file);
				m_lastReadTime = m_file.lastModified();
			}
			System.err.println("Shut down looking at file " + m_file);
		}

		@Override
		public void close() throws IOException {
			m_shutdownLatch.countDown();
		}

	}

	public static void main(String[] args) throws MalformedURLException, IOException, InterruptedException {
		if (System.getenv("CLI_DEBUG") != null) {
			// want verbose logging
		} else {
			Logger.getLogger("io.socket").setLevel(Level.WARNING);
		}
		CLIArgs cliArgs = new CLIArgs(args);
		if (cliArgs.error) {
			System.exit(1);
		}
		System.out.println(String.format("URL: %s, StopFile: %s, InputFiles: %s", cliArgs.url, cliArgs.stopFile, cliArgs.inputFiles));
		// RemoteMessageSender messageSender = new MySocketIO(cliArgs.url);
		RemoteMessageSender messageSender = new MyHttpPostClient(cliArgs.url);

		newFileReader2(messageSender, cliArgs.stopFile, cliArgs.inputFiles);
	}

	private static class CLIArgs {

		private boolean error;
		private File stopFile;
		private Map<String, File> inputFiles = new HashMap<String, File>();
		private URL url;

		public CLIArgs(String[] args) {
			if (args.length == 0) {
				System.err.println("First argument is a url, the second and on arguments are key:file pairs");
				this.error = true;
				return;
			}
			int indexPos = 0;
			if (args[indexPos].equals("-p") || args[indexPos].equals("-l")) {
				try {
					this.url = new URL(args[indexPos].equals("-p") ? PROD_SERVER_URL : LOCAL_SERVER_URL);
				} catch (MalformedURLException ex) {
					System.err.println("Bad URL: " + url + " : " + ex);
					this.error = true;
				}
				this.stopFile = new File(DEFAULT_STOP_FILE);
				indexPos++;
			} else {
				try {
					this.url = new URL(args[indexPos++]);
				} catch (MalformedURLException ex) {
					System.err.println("Bad URL: " + url + " : " + ex);
					this.error = true;
				}
				this.stopFile = new File(args[indexPos++]);
			}
			if (args[indexPos].equals("-s")) {
				// standard file setup
				indexPos++;
				inputFiles.put("nodes", new File(args[indexPos++]));
				inputFiles.put("bars", new File(args[indexPos++]));
				inputFiles.put("force_nodes", new File(args[indexPos++]));
				inputFiles.put("force_bars", new File(args[indexPos++]));
			}
			while (indexPos < args.length) {
				String[] keyValue = args[indexPos++].split(":", 2);
				inputFiles.put(keyValue[0], new File(keyValue[1]));
			}
			this.error = false;
		}

	}

}
