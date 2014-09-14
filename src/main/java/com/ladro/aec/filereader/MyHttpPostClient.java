package com.ladro.aec.filereader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class MyHttpPostClient implements RemoteMessageSender {

	private final URL m_url;
	private CloseableHttpClient m_httpclient;

	public MyHttpPostClient(URL url) {
		this.m_url = url;
		this.m_httpclient = HttpClients.createDefault();
	}

	@Override
	public void sendMessage(String channel, String... lines) {

	}

	@Override
	public void sendMessage(String channel, File file) {
		try {
			doit(file, new URL(m_url.toString() + "/" + channel).toURI());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	private void doit(File file, URI url) throws IOException {
		System.err.println("Posting file " + file + " to " + url);
		HttpPost post = new HttpPost(url);
		//HttpEntity httpEntity = EntityBuilder.create().gzipCompress().setFile(file).build();
		//HttpEntity httpEntity = EntityBuilder.create().setFile(file).build();
		HttpEntity httpEntity = MultipartEntityBuilder.create().addPart("data", new FileBody(file)).build();
		//post.setEntity(new GzipCompressingEntity(httpEntity));
		post.setEntity(httpEntity);

		CloseableHttpResponse res = m_httpclient.execute(post);

		res.close();

	}

}
