package com.ladro.aec.filereader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class TailInputStream extends InputStream {
	private final InputStream in;

	public TailInputStream(File file) throws FileNotFoundException {
		in = new FileInputStream(file);
	}

	@Override
	public int available() throws IOException {
		return in.available();
	}

	public int read() throws IOException {
		int readByte = -1;
		do {
			if (in.available() > 0) {
				if ((readByte = in.read()) != -1) {
					break;
				}
			}
			try {
				Thread.sleep(9);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} while (readByte == -1);

		return readByte;
	}

	public int read(byte b[], int off, int len) throws IOException {
		if (b == null) {
			throw new NullPointerException();
		} else if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return 0;
		}

		int c = read();
		if (c == -1) {
			return -1;
		}
		b[off] = (byte) c;

		return 1;
	}

	public void close() throws IOException {
		in.close();
	}
}
