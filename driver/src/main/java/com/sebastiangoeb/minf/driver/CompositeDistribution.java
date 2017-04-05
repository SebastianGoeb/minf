package com.sebastiangoeb.minf.driver;

import org.apache.commons.math3.distribution.ConstantRealDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CompositeDistribution {
	private static Pattern termPattern = Pattern.compile("(\\w+)\\((.*)\\)");

	private String sourceString;
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

	CompositeDistribution(String expr) {
		String[] terms = expr.replace("\\s", "").split("\\+");
		List<RealDistribution> dists = new ArrayList<>();
		for (String term : terms) {
			Matcher m = termPattern.matcher(term);
			if (m.matches()) {
				String type = m.group(1).toLowerCase();
				List<Double> args = new ArrayList<>();
				for (String arg : m.group(2).split(",")) {
					args.add(Double.parseDouble(arg));
				}
				dists.add(getRealDistribution(type, args));
			} else {
				throw new IllegalArgumentException("Invalid term: " + term);
			}
		}
		this.sourceString = expr;
		this.distributions = dists;
	}

	double sample() {
		RealDistribution dist = distributions.get(new Random().nextInt(distributions.size()));
		double sample = dist.sample();
		while (sample < 0 || 1 < sample) {
			sample = dist.sample();
		}
		return sample;
	}

	private double cycleMean(double mean, double time, double cycle) {
		double r = (mean + 2.0 * time / cycle) % 2.0;
		if (r <= 1) {
			return r;
		} else {
			return 2 - r;
		}
	}

	double sample(double time, double cycle) {
		RealDistribution protoDist = distributions.get(new Random().nextInt(distributions.size()));
		RealDistribution dist;
		if (protoDist instanceof ConstantRealDistribution) {
			return cycleMean(protoDist.getNumericalMean(), time, cycle);
		} else if (protoDist instanceof UniformRealDistribution) {
			dist = protoDist;
		} else if (protoDist instanceof NormalDistribution) {
			NormalDistribution normalProtoDist = (NormalDistribution) protoDist;
			dist = new NormalDistribution(cycleMean(normalProtoDist.getNumericalMean(), time, cycle), normalProtoDist.getStandardDeviation());
		} else {
			throw new IllegalStateException("Distribution not supported: " + protoDist.getClass().getSimpleName());
		}

		double sample = dist.sample();
		while (sample < 0 || 1 < sample) {
			sample = dist.sample();
		}
		return sample;
	}

	@Override
	public String toString() {
		return sourceString;
	}
}
