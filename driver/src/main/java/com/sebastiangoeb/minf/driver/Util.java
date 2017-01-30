package com.sebastiangoeb.minf.driver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;

public class Util {

	public static double parseUnits(String withUnits) {
		if (withUnits == null) {
			return 0;
		}

		Matcher m = Pattern.compile("^([0-9.]+)([a-zA-Z]*)").matcher(withUnits);
		m.matches();
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
	}

	public static InetAddress parseIP(String address) {
		String[] toks = address.split(".");
		byte[] bytes = new byte[toks.length];
		for (int i = 0; i < toks.length; i++) {
			bytes[i] = Byte.parseByte(toks[i]);
		}
		try {
			return InetAddress.getByAddress(bytes);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String formatIP(double val) {
		long raw = (long) (val * Math.pow(2, 32));
		return MessageFormat.format("{0}.{1}.{2}.{3}", (raw >> 24) & 255, (raw >> 16) & 255, (raw >> 8) & 255, raw & 255);
	}

	public static double sampleDistributions(List<DistributionInfo> distributions) {
		DistributionInfo dist;
		if (distributions.size() > 1) {
			List<Pair<DistributionInfo, Double>> weightedDists = distributions.stream().map(d -> new Pair<>(d, d.weight))
					.collect(Collectors.toList());
			EnumeratedDistribution<DistributionInfo> distOfDists = new EnumeratedDistribution<DistributionInfo>(
					weightedDists);
			dist = distOfDists.sample();
		} else {
			dist = distributions.get(0);
		}
		return dist.sample();
	}
}
