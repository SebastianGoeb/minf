package com.sebastiangoeb.minf.driver;

import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Util {

	static double parseUnits(String withUnits) {
		if (withUnits == null) {
			return 0;
		}

		Matcher m = Pattern.compile("^([0-9.]+)([a-zA-Z]*)").matcher(withUnits);
		if (m.matches()) {
			double val = Double.parseDouble(m.group(1));
			switch (m.group(2)) {
				// Unitless
				case "":
					return val;
				// Bytes
				case "k":
				case "kB":
				case "K":
				case "KB":
					return val * Math.pow(2, 10);
				case "M":
				case "MB":
					return val * Math.pow(2, 20);
				case "G":
				case "GB":
					return val * Math.pow(2, 30);
				case "T":
				case "TB":
					return val * Math.pow(2, 40);
				case "P":
				case "PB":
					return val * Math.pow(2, 50);
				// Seconds
				case "s":
					return val;
				case "ms":
					return val * 10e-3;
				case "us":
					return val * 10e-6;
				case "ns":
					return val * 10e-9;
			}
			return 0;
		} else {
			throw new IllegalArgumentException("Invalid units: " + withUnits);
		}
	}
	
	static int ip2int(String ipString) {
		String[] octets = ipString.split("\\.");
		int ipVal = 0;
		for (int i = 0; i < octets.length; i++) {
			int octetVal = Integer.parseInt(octets[i]);
			ipVal += octetVal << (32 - 8 - 8 * i);
		}
		return ipVal;
	}
	
	static String int2ip(int ipVal) {
		return MessageFormat.format("{0}.{1}.{2}.{3}", (ipVal >> 24) & 255, (ipVal >> 16) & 255, (ipVal >> 8) & 255, ipVal & 255);
	}
}
