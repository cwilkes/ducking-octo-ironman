package com.ladro.aec.filereader;

import java.io.File;

public interface RemoteMessageSender {

	void sendMessage(String channel, String... lines);

	void sendMessage(String channel, File file);

}
