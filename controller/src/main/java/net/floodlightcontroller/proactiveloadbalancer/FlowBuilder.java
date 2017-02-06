package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.collect.Ordering;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.*;
import java.util.stream.Collectors;

class FlowBuilder {

	static Set<LoadBalancingFlow> buildFlowsUniform(Topology topology, Set<LoadBalancingFlow> flows) {
		// sort dips
		List<Host> sortedHosts = Ordering.from(Comparator.comparing(Host::getDip)).immutableSortedCopy(topology.getHosts());

		// scale weights to int values summing to next power of 2
		int bits = sortedHosts.size() == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(sortedHosts.size() - 1);
		List<Double> weights = sortedHosts.stream()
                .map(h -> h.getWeight())
				.collect(Collectors.toList());
		List<Integer> scaledWeights = FlowBuilder.scaleWeights(weights,1 << bits);

		// Split weights into powers of 2
		List<IPv4Address> splitDips = new ArrayList<>();
		List<Integer> splitWeights = new ArrayList<>();
		for (int i = 0; i < scaledWeights.size(); i++) {
			int weight = scaledWeights.get(i);
			for (int j = 1; j <= weight; j <<= 1) {
				if ((j & weight) != 0) {
					splitWeights.add(j);
					splitDips.add(sortedHosts.get(i).getDip());
				}
			}
		}

		// Turn into prefixes
		Set<LoadBalancingFlow> newFlows = new LinkedHashSet<>();
		int valueIncrement = Integer.MIN_VALUE >>> (ProactiveLoadBalancer.CLIENT_RANGE.getMask().asCidrMaskLength() + bits - 1);
		int value = ProactiveLoadBalancer.CLIENT_RANGE.getValue().getInt();
		int mask = IPv4Address.ofCidrMaskLength((ProactiveLoadBalancer.CLIENT_RANGE.getMask().asCidrMaskLength() + bits)).getInt();
		for (int i = 0; i < splitWeights.size(); i++) {
			int weight = splitWeights.get(i);
			IPv4AddressWithMask prefix = IPv4Address.of(value).withMask(IPv4Address.of(mask * weight));
			newFlows.add(new LoadBalancingFlow(prefix, splitDips.get(i)));
			value += valueIncrement * weight;
		}

		return newFlows;
	}

	static Set<LoadBalancingFlow> buildFlowsGreedy(Topology topology, Set<LoadBalancingFlow> flows, PrefixTrie<Long> traffic) {
	    // TODO
        return Collections.emptySet();
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
