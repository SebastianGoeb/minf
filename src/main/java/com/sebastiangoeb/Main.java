package com.sebastiangoeb;

import static spark.Spark.get;
import static spark.Spark.port;

import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

	private static final int BUFFER_SIZE = 64 * 1024; // 64 KB

	public static void main(String[] args) {
		port(8080);
		get("/:bytes", (req, res) -> {
			// Parse params
			long totalSize = human2bytes(req.params(":bytes"));

			// Set headers and status
			res.header("Content-Length", String.valueOf(totalSize));

			// Stream zeros
			res.raw().setBufferSize(BUFFER_SIZE);
			OutputStream out = res.raw().getOutputStream();
			byte[] buffer = new byte[BUFFER_SIZE];
			for (long bytesWritten = 0; bytesWritten < totalSize; bytesWritten += Math.min(totalSize - bytesWritten,
					BUFFER_SIZE)) {
				out.write(buffer, 0, (int) Math.min(totalSize - bytesWritten, BUFFER_SIZE));
			}
			return "";
		});
	}

	private static long human2bytes(String humanReadable) {
		if (humanReadable == null) {
			return -1;
		}

		Matcher m = Pattern.compile("^(\\d+)([kKMGTP]?)$").matcher(humanReadable);
		m.matches();
		long bytes = Long.parseLong(m.group(1));
		switch (m.group(2)) {
		case "k":
		case "K":
			bytes <<= 10;
			break;
		case "M":
			bytes <<= 20;
			break;
		case "G":
			bytes <<= 30;
			break;
		case "T":
			bytes <<= 40;
			break;
		case "P":
			bytes <<= 50;
			break;
		}
		return bytes;
	}
}
