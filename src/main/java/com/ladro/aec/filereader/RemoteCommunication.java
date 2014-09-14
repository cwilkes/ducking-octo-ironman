package com.ladro.aec.filereader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class RemoteCommunication implements RemoteMessageSender {

	private final URL url;
	private final BlockingQueue<String> queuedMessages = new LinkedBlockingQueue<String>();
	private final ExecutorService m_execService;
	private final static String STOP_WORD = "aslkjdasljsaljkdasljkasdljsadlsadljkdsaljdsasa";

	public RemoteCommunication(URL url) throws IOException {
		this.url = url;
		m_execService = Executors.newFixedThreadPool(1);
		m_execService.submit(new MySender());
		// no other tasks needed
		m_execService.shutdown();
	}

	@Override
	public void sendMessage(String channel, String... lines) {
		for (String line : lines)
			queuedMessages.add(line);
	}

	public void shutdown() {
		System.err.println("Shutting down");
		queuedMessages.clear();
		queuedMessages.add(STOP_WORD);
		m_execService.shutdownNow();
	}

	private final class MySender implements Runnable {

		@Override
		public void run() {
			while (true) {
				String msg = null;
				try {
					msg = queuedMessages.take();
					if (STOP_WORD.equals(msg)) {
						break;
					}
					sendRemoteMessage(msg);
				} catch (InterruptedException e) {
					e.printStackTrace();
					break;
				} catch (IOException e) {
					System.err.println("Error with sending message '" + msg + "'");
					// this can be okay
					e.printStackTrace();
				}
			}
			System.err.println("Shut down thread");
		}

	}

	private void sendRemoteMessage(String line) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "text/plain");
		connection.setRequestProperty("Content-Length", Integer.toString(line.getBytes().length));
		connection.setRequestProperty("Content-Language", "en-US");
		OutputStream os = connection.getOutputStream();
		os.write(line.getBytes());
		os.close();
		BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
		byte[] buf = new byte[1024];
		@SuppressWarnings("unused")
		int s = 0;
		while ((s = bis.read(buf)) != -1) {
			//
		}
		bis.close();
		connection.disconnect();
	}

	@Override
	public void sendMessage(String channel, File file) {
		// TODO Auto-generated method stub
		
	}

}
