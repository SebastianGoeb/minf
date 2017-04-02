package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.*;
import net.floodlightcontroller.proactiveloadbalancer.util.IPUtil;
import net.floodlightcontroller.proactiveloadbalancer.util.PrefixTrie;
import net.floodlightcontroller.proactiveloadbalancer.util.PrefixTrie.Node;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

class FlowBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(FlowBuilder.class);

    static List<LoadBalancingFlow> buildConnectionFlows(List<LoadBalancingFlow> flows, Map<IPv4Address, Double> rates, Map<IPv4Address, Double> weights) {
        if (flows == null) {
            flows = emptyList();
        }

        // Find least utilized server (weighted)
        IPv4Address leastUtilizedServer = rates.entrySet().stream()
                .min(comparing(e -> e.getValue() / weights.get(e.getKey())))
                .map(e -> e.getKey())
                .orElse(null);

        // Put any new flows on that server
        List<LoadBalancingFlow> newFlows = new ArrayList<>(flows.size());
        for (LoadBalancingFlow flow : flows) {
            if (flow.getDip() != null) {
                newFlows.add(flow);
            } else {
                newFlows.add(new LoadBalancingFlow(flow.getPrefix(), leastUtilizedServer));
            }
        }
        return newFlows;
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

    static Map<DatapathId, List<LoadBalancingFlow>> buildPhysicalFlows(Topology topology, Map<DatapathId, IPv4Address> vips, List<LoadBalancingFlow> logicalFlows) {
        List<DatapathId> toposortedSwitches = toposortSwitches(topology);

        // Group flows by server
        Map<IPv4Address, List<LoadBalancingFlow>> logicalFlowsGroupedByServer = new HashMap<>();
        for (IPv4Address dip : topology.getServers()) {
            logicalFlowsGroupedByServer.put(dip, logicalFlows.stream().filter(flow -> flow.getDip().equals(dip)).collect(toList()));
        }

        // Calculate downstream flows per switch in dependency order
        Map<DatapathId, List<LoadBalancingFlow>> physicalFlowsGroupedBySwitch = new HashMap<>();
        for (DatapathId switchId : toposortedSwitches) {
            List<LoadBalancingFlow> flowsFromSwitch = new ArrayList<>();
            // Add switch -> downstream server flows without modification
            Map<IPv4Address, Integer> downstreamServersAndPorts = topology.getDownlinksToServers().get(switchId);
            for (IPv4Address server : downstreamServersAndPorts.keySet()) {
                List<LoadBalancingFlow> logicalFlowsToServer = logicalFlowsGroupedByServer.get(server);
                flowsFromSwitch.addAll(logicalFlowsToServer);
            }
            // Add switch -> downstream switch flows, replacing destination IPs with downstream switch VIPs
            Map<DatapathId, Integer> downstreamSwitchesAndPorts = topology.getDownlinksToSwitches().get(switchId);
            for (DatapathId downstreamSwitch : downstreamSwitchesAndPorts.keySet()) {
                IPv4Address downstreamSwitchVip = vips.get(downstreamSwitch);
                List<LoadBalancingFlow> physicalFlowsToSwitch = physicalFlowsGroupedBySwitch.get(downstreamSwitch).stream()
                        .map(flow -> new LoadBalancingFlow(flow.getPrefix(), downstreamSwitchVip))
                        .collect(toList());
                flowsFromSwitch.addAll(physicalFlowsToSwitch);
            }
            // Merge flows if adjacent and same destination
            physicalFlowsGroupedBySwitch.put(switchId, mergeContiguousFlows(flowsFromSwitch));
        }

        return physicalFlowsGroupedBySwitch;
    }

    // Helpers
    static List<LoadBalancingFlow> mergeContiguousFlows(List<LoadBalancingFlow> flows) {
        Map<IPv4AddressWithMask, IPv4Address> flowsAsMap = flows.stream().collect(toMap(
                flow -> flow.getPrefix(),
                flow -> flow.getDip()));
        PrefixTrie<IPv4Address> tree = PrefixTrie.inflate(IPv4AddressWithMask.of("0.0.0.0/0"),
                null, flows.stream().map(LoadBalancingFlow::getPrefix).collect(toList()));
        tree.traversePostOrder((node, prefix) -> {
            if (flowsAsMap.containsKey(prefix)) {
                node.setValue(flowsAsMap.get(prefix));
            }
            Node<IPv4Address> child0 = node.getChild0();
            Node<IPv4Address> child1 = node.getChild1();
            if (child0 != null && child1 != null) {
                IPv4Address ip0 = child0.getValue();
                IPv4Address ip1 = child1.getValue();
                if (ip0 != null && ip1 != null && ip0.equals(ip1)) {
                    node.collapse();
                    node.setValue(ip0);
                }
            }
        });

        List<LoadBalancingFlow> result = new ArrayList<>();
        tree.traversePreOrder((node, prefix) -> {
            if (node.getValue() != null) {
                result.add(new LoadBalancingFlow(prefix, node.getValue()));
            }
        });

        return result;
    }

    private static PrefixTrie<Double> mergeMeasurements(Collection<Measurement> measurements, Config config) {
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
