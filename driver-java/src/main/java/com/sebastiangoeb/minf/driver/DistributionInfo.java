package com.sebastiangoeb.minf.driver;

import org.apache.commons.math3.distribution.ConstantRealDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

public class DistributionInfo {

	private RealDistribution dist;

	public Type type;
	public Double mean;
	public Double sd;
	public Double left;
	public Double right;
	public Double weight;

	public RealDistribution getDist() {
		if (dist == null) {
			switch (type) {
			case CONSTANT:
				dist = new ConstantRealDistribution(mean);
				break;
			case UNIFORM:
				dist = new UniformRealDistribution(left, right);
				break;
			case NORMAL:
				dist = new NormalDistribution(mean, sd);
				break;
			}
		}
		return dist;
	}

	public double sample() {
		getDist();

		switch (type) {
		case CONSTANT:
		case UNIFORM:
			return dist.sample();
		default:
			double sample = dist.sample();
			while ((left == null || sample <= left) && (right == null || right < sample)) {
				sample = dist.sample();
			}
			return sample;
		}
	}

	public static enum Type {
		CONSTANT, UNIFORM, NORMAL
	}
}
