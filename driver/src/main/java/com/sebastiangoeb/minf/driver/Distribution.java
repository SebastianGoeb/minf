package com.sebastiangoeb.minf.driver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.distribution.ConstantRealDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

public class Distribution {
	private static Pattern termPattern = Pattern.compile("(\\w+)\\((.*)\\)");
	
	private List<RealDistribution> distributions;

	private static RealDistribution getRealDistribution(String type, List<Double> args) {
		switch (type) {
		case "c":
		case "constant":
			double value = args.get(0);
			return new ConstantRealDistribution(value);
		case "u":
		case "uniform":
			double min = args.get(0);
			double max = args.get(1);
			return new UniformRealDistribution(min, max);
		case "n":
		case "normal":
			double mean = args.get(0);
			double sd = Math.sqrt(args.get(1));
			return new NormalDistribution(mean, sd);
		default:
			return null;
		}
	}

	private Distribution(List<RealDistribution> distributions) {
		this.distributions = distributions;
	}

	public static Distribution fromString(String expr) {
		String[] terms = expr.replace("\\s", "").split("\\+");
		List<RealDistribution> dists = new ArrayList<>();
		for (String term : terms) {
			Matcher m = termPattern.matcher(term);
			m.matches();
			String type = m.group(1).toLowerCase();
			List<Double> args = new ArrayList<>();
			for (String arg : m.group(2).split(",")) {
				args.add(Double.parseDouble(arg));
			}
			dists.add(getRealDistribution(type, args));
		}

		return new Distribution(dists);
	}

	public double sample() {
		RealDistribution dist = distributions.get(new Random().nextInt(distributions.size()));
		double sample = dist.sample();
		while (sample < 0 || 1 < sample) {
			sample = dist.sample();
		}
		return sample;
	}
}
