package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.collect.Ordering;
import net.floodlightcontroller.proactiveloadbalancer.domain.*;
import net.floodlightcontroller.proactiveloadbalancer.util.IPUtil;
import net.floodlightcontroller.proactiveloadbalancer.util.PrefixTrie;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

class FlowBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(FlowBuilder.class);

    static List<LoadBalancingFlow> buildFlowsClassic(Config config, List<LoadBalancingFlow> flows, PrefixTrie<Double> traffic) {
        // TODO do this more cleanly
        if (flows == null) {
            flows = emptyList();
        }

        List<IPv4Address> servers = config.getTopology().getServers();
        Random random = new Random();

        // TODO respect weights
        Map<IPv4Address, Double> weights = config.getWeights();

        // TODO in place?
        ArrayList<LoadBalancingFlow> newFlows = new ArrayList<>(flows);
        newFlows.replaceAll(flow -> {
            if (flow.getDip() != null) {
                return flow;
            } else {
                IPv4Address randomDip = servers.get(random.nextInt(servers.size()));
                return new LoadBalancingFlow(flow.getPrefix(), randomDip);
            }
        });
        return newFlows;
    }

	static List<LoadBalancingFlow> buildFlowsUniform(Config config, List<LoadBalancingFlow> flows) {
        Map<IPv4Address, Double> weights = config.getWeights();
        Topology topology = config.getTopology();
        IPv4AddressWithMask clientRange = IPUtil.base(config.getClientRange());

        // sort dips
		List<IPv4Address> sortedServers = Ordering.natural().immutableSortedCopy(topology.getServers());

		// scale weights to int values summing to next power of 2
		int bits = sortedServers.size() == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(sortedServers.size() - 1);
		List<Double> sortedWeights = sortedServers.stream()
                .map(server -> weights.get(server))
				.collect(toList());
		List<Integer> scaledWeights = FlowBuilder.scaleWeights(sortedWeights,1 << bits);

		// Split weights into powers of 2
		List<IPv4Address> splitDips = new ArrayList<>();
		List<Integer> splitWeights = new ArrayList<>();
		for (int i = 0; i < scaledWeights.size(); i++) {
			int weight = scaledWeights.get(i);
			for (int j = 1; j <= weight; j <<= 1) {
				if ((j & weight) != 0) {
					splitWeights.add(j);
					splitDips.add(sortedServers.get(i));
				}
			}
		}

		// Turn into prefixes
		List<LoadBalancingFlow> newFlows = new ArrayList<>();
		int valueIncrement = Integer.MIN_VALUE >>> (clientRange.getMask().asCidrMaskLength() + bits - 1);
		int value = clientRange.getValue().getInt();
		int mask = IPv4Address.ofCidrMaskLength((clientRange.getMask().asCidrMaskLength() + bits)).getInt();
		for (int i = 0; i < splitWeights.size(); i++) {
			int weight = splitWeights.get(i);
			IPv4AddressWithMask prefix = IPv4Address.of(value).withMask(IPv4Address.of(mask * weight));
			newFlows.add(new LoadBalancingFlow(prefix, splitDips.get(i)));
			value += valueIncrement * weight;
		}

		return newFlows;
	}

	static List<LoadBalancingFlow> buildFlowsNonUniform(Config config, List<LoadBalancingFlow> flows, PrefixTrie<Double> traffic) {
	    // TODO don't defer to uniform algorithm
        return buildFlowsUniform(config, flows);
	}

	static Map<DatapathId, List<LoadBalancingFlow>> buildPhysicalFlows(Config config, List<LoadBalancingFlow> logicalFlows, Map<DatapathId, IPv4Address> vips) {
        List<DatapathId> toposortedDpids = toposortSwitches(config.getTopology());

        // Group flows by dip
        // TODO do this more cleanly, or even per switch?
        Map<IPv4Address, List<LoadBalancingFlow>> flowsByDip = new HashMap<>();
        for (IPv4Address dip : config.getTopology().getServers()) {
            flowsByDip.put(dip, logicalFlows.stream().filter(flow -> flow.getDip().equals(dip)).collect(toList()));
        }

        // Calculate downstream flow s for load balancers
        Map<DatapathId, List<LoadBalancingFlow>> flowsByDpid = new HashMap<>();
        for (DatapathId dpid : toposortedDpids) {
            Map<DatapathId, Integer> switches = config.getTopology().getDownlinksToSwitches().get(dpid);
            Map<IPv4Address, Integer> servers = config.getTopology().getDownlinksToServers().get(dpid);
            flowsByDpid.put(dpid, new ArrayList<>());
            // Add flows to servers
            for (IPv4Address dip : servers.keySet()) {
                flowsByDpid.get(dpid).addAll(flowsByDip.get(dip));
            }
            // Add previously calculated flows to this switch
            for (DatapathId downstreamDpid : switches.keySet()) {
                IPv4Address downstreamVip = vips.get(downstreamDpid);
                flowsByDpid.get(dpid).addAll(flowsByDpid.get(downstreamDpid).stream()
                        .map(flow -> new LoadBalancingFlow(flow.getPrefix(), downstreamVip))
                        .collect(toList()));
            }
        }

        // TODO group flows into superflows

        return flowsByDpid;
    }

    static List<IPv4AddressWithMask> buildMeasurementFlows(Collection<Measurement> measurements, Config config) {
        Objects.requireNonNull(measurements);

        double total = measurements.stream()
                .mapToLong(Measurement::getBytes)
                .sum();

        IPv4AddressWithMask rootPrefix = IPUtil.base(config.getClientRange());

        // Merge into tree
        PrefixTrie<Double> tree = mergeMeasurements(measurements, config);

        // Expand if above threshold, collapse if below
        tree.traversePreOrder((node, prefix) -> {
            double effectiveValue = total != 0 ? node.getValue() : Math.pow(2, rootPrefix.getMask().asCidrMaskLength() - prefix.getMask().asCidrMaskLength());
            if (effectiveValue > config.getMeasurementThreshold() && node.isLeaf() && prefix.getMask().asCidrMaskLength() < 32) {
                node.expand(node.getValue() / 2, node.getValue() / 2);
            } else if (effectiveValue <= config.getMeasurementThreshold() && !node.isLeaf()) {
                node.collapse();
            }
        });

        // Extract leaf node prefixes;
        List<IPv4AddressWithMask> prefixes = new ArrayList<>();
        tree.traversePostOrder((node, prefix) -> {
            if (node.isLeaf()) {
                prefixes.add(prefix);
            }
        });

        return prefixes;
    }

    static List<ForwardingFlow> buildForwardingFlows(DatapathId dpid, Config config, Map<DatapathId, IPv4Address> vips) {
        List<ForwardingFlow> forwardingFlows = new ArrayList<>();
        // Downlinks to switches
        config.getTopology().getDownlinksToSwitches().get(dpid).forEach((downstreamDpid, portno) -> {
            IPv4AddressWithMask prefix = vips.get(downstreamDpid).withMaskOfLength(32);
            forwardingFlows.add(new ForwardingFlow(prefix, portno));
        });
        // Downlinks to servers
        config.getTopology().getDownlinksToServers().get(dpid).forEach((dip, portno) -> {
            IPv4AddressWithMask prefix = dip.withMaskOfLength(32);
            forwardingFlows.add(new ForwardingFlow(prefix, portno));
        });
        // Uplinks to switches
        config.getTopology().getUplinksToSwitches().get(dpid).forEach((upstreamDpid, portno) -> {
            IPv4AddressWithMask prefix = IPUtil.base(config.getClientRange());
            forwardingFlows.add(new ForwardingFlow(prefix, portno));
        });
        // Uplinks to clients
        config.getTopology().getUplinksToClients().get(dpid).forEach((prefix, portno) -> {
            forwardingFlows.add(new ForwardingFlow(prefix, portno));
        });
        return forwardingFlows;
    }

    static PrefixTrie<Double> mergeMeasurements(Collection<Measurement> measurements, Config config) {
        Objects.requireNonNull(measurements);

        double total = measurements.stream()
                .mapToLong(Measurement::getBytes)
                .sum();

        PrefixTrie<Double> tree = PrefixTrie.empty(IPUtil.base(config.getClientRange()), 0D);
        Queue<Measurement> measurementsInPreOrder = new PriorityQueue<>(comparing(Measurement::getPrefix));
        measurementsInPreOrder.addAll(measurements);

        // Expand tree, fill in measurements, and propagate estimates down
        tree.traversePreOrder((node, prefix) -> {
            Measurement nextMeasurement = measurementsInPreOrder.peek();
            while (nextMeasurement != null && Objects.equals(prefix, nextMeasurement.getPrefix())) {
                node.setValue(node.getValue() + (total == 0 ? 0 : nextMeasurement.getBytes() / total));
                measurementsInPreOrder.remove();
                nextMeasurement = measurementsInPreOrder.peek();
            }
            if (nextMeasurement != null && prefix.contains(nextMeasurement.getPrefix().getValue())) {
                IPv4Address minOfRightSubtree = IPUtil.min(IPUtil.subprefix1(prefix));
                IPv4Address maxOfLeftSubtree = IPUtil.max(IPUtil.subprefix0(prefix));
                if (minOfRightSubtree.compareTo(config.getClientRange().getMin()) <= 0) { // Left subtree (0) not relevant
                    node.expand1(node.getValue());
                } else if (config.getClientRange().getMax().compareTo(maxOfLeftSubtree) <= 0) { // Right subtree (1) not relevant
                    node.expand0(node.getValue());
                } else {
                    node.expand(node.getValue() / 2, node.getValue() / 2);
                }
            }
        });
        // Propagate measurements up
        tree.traversePostOrder((node, prefix) -> {
            if (!node.isLeaf()) {
                double val = 0;
                if (node.getChild0() != null) {
                    val += node.getChild0().getValue();
                }
                if (node.getChild1() != null) {
                    val += node.getChild1().getValue();
                }
                node.setValue(val);
            }
        });
        return tree;
    }

	// Helpers
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

    private static List<DatapathId> toposortSwitches(Topology topology) {
        // Topological sort over load balancers (sort by dependency)
        Map<DatapathId, List<DatapathId>> dependencies = new HashMap<>();
        Map<DatapathId, List<DatapathId>> dependents = new HashMap<>();
        for (DatapathId dpid : topology.getDownlinksToSwitches().keySet()) {
            dependencies.put(dpid, new ArrayList<>());
            dependents.put(dpid, new ArrayList<>());
        }
        for (DatapathId dependent : topology.getDownlinksToSwitches().keySet()) {
            for (DatapathId dependency : topology.getDownlinksToSwitches().get(dependent).keySet()) {
                dependencies.get(dependent).add(dependency);
                dependents.get(dependency).add(dependent);
            }
        }
        List<DatapathId> toposortedDpids = new ArrayList<>();
        List<DatapathId> frontier = dependencies.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(e -> e.getKey())
                .collect(toList());
        while(!frontier.isEmpty()) {
            DatapathId dpid = frontier.remove(0);
            toposortedDpids.add(dpid);
            for (DatapathId dependent : dependents.get(dpid)) {
                dependencies.get(dependent).remove(dpid);
                if (dependencies.get(dependent).isEmpty()) {
                    dependencies.remove(dependent);
                    frontier.add(dependent);
                }
            }
            dependents.remove(dpid);
        }
        return toposortedDpids;
    }
}
