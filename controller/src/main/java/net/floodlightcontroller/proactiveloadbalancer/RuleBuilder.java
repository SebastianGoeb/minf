package net.floodlightcontroller.proactiveloadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import com.google.common.collect.Ordering;

class RuleBuilder {

	// Contants
	private static final IPv4AddressWithMask SRC_RANGE = IPv4AddressWithMask.of("10.5.0.0/16");

	public static Set<Rule> buildRules(AddressPool addressPool) {
		// sort dips
		List<IPv4Address> dips = Ordering.natural().immutableSortedCopy(addressPool.getDips());

		// scale weights to int values summing to next power of 2
		int bits = dips.size() == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(dips.size() - 1);
		List<Integer> scaledWeights = RuleBuilder.scaleWeights(Collections.nCopies(dips.size(), 1d), 1 << bits);
		
		// Split weights into powers of 2
		List<IPv4Address> splitDips = new ArrayList<>();
		List<Integer> splitWeights = new ArrayList<>();
		for (int i = 0; i < scaledWeights.size(); i++) {
			int weight = scaledWeights.get(i);
			for (int j = 1; j <= weight; j <<= 1) {
				if ((j & weight) != 0) {
					splitWeights.add(j);
					splitDips.add(dips.get(i));
				}
			}
		}

		// Turn into prefixes
		Set<Rule> rules = new LinkedHashSet<>();
		int valueIncrement = Integer.MIN_VALUE >>> (SRC_RANGE.getMask().asCidrMaskLength() + bits - 1);
		int value = SRC_RANGE.getValue().getInt();
		int mask = IPv4Address.ofCidrMaskLength((SRC_RANGE.getMask().asCidrMaskLength() + bits)).getInt();
		for (int i = 0; i < splitWeights.size(); i++) {
			int weight = splitWeights.get(i);
			IPv4AddressWithMask prefix = IPv4Address.of(value).withMask(IPv4Address.of(mask * weight));
			rules.add(new Rule(prefix, splitDips.get(i)));
			value += valueIncrement * weight;
		}

		return rules;
	}
	
	private static List<Integer> scaleWeights(List<Double> weights, double total) {
		// Cumulative sum
		double[] cumul = new double[weights.size()];
		double sum = 0;
		for (int i = 0; i < cumul.length; i++) {
			sum += weights.get(i);
			cumul[i] = sum;
		}

		// Scale and round
		int[] scaled = new int[cumul.length];
		for (int i = 0; i < cumul.length; i++) {
			scaled[i] = (int) Math.round(cumul[i] * total / sum);
		}

		// Un-cumulative
		List<Integer> normalizedWeights = new ArrayList<>(scaled.length);
		normalizedWeights.add(scaled[0]);
		for (int i = 1; i < scaled.length; i++) {
			normalizedWeights.add(scaled[i] - scaled[i - 1]);
		}
		return normalizedWeights;
	}
}
