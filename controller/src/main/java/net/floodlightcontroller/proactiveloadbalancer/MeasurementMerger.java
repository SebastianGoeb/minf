package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.WeightedPrefix;
import net.floodlightcontroller.proactiveloadbalancer.util.IPUtil;
import net.floodlightcontroller.proactiveloadbalancer.util.IPv4AddressRange;
import net.floodlightcontroller.proactiveloadbalancer.util.PrefixTrie;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;

class MeasurementMerger {

    public static List<WeightedPrefix> merge(List<WeightedPrefix> measurements, IPv4AddressRange clientRange) {
        if (measurements.isEmpty()) {
            return emptyList();
        } else if (measurements.size() == 1) {
            return measurements;
        }

        IPv4AddressWithMask rootPrefix = IPUtil.base(clientRange);

        PrefixTrie<Double> tree = PrefixTrie.empty(rootPrefix, 0D);
        Queue<WeightedPrefix> measurementsInPreOrder = new PriorityQueue<>(comparing(WeightedPrefix::getPrefix));
        measurementsInPreOrder.addAll(measurements);

        // Expand tree, fill in measurements, and propagate estimates down
        tree.traversePreOrder((node, prefix) -> {
            WeightedPrefix nextMeasurement = measurementsInPreOrder.peek();
            while (nextMeasurement != null && Objects.equals(prefix, nextMeasurement.getPrefix())) {
                node.setValue(node.getValue() + nextMeasurement.getWeight());
                measurementsInPreOrder.remove();
                nextMeasurement = measurementsInPreOrder.peek();
            }
            if (nextMeasurement != null && prefix.contains(nextMeasurement.getPrefix().getValue())) {
                IPv4Address minOfRightSubtree = IPUtil.min(IPUtil.subprefix1(prefix));
                IPv4Address maxOfLeftSubtree = IPUtil.max(IPUtil.subprefix0(prefix));
                if (minOfRightSubtree.compareTo(clientRange.getMin()) <= 0) { // Left subtree (0) not relevant
                    node.expand1(node.getValue());
                } else if (clientRange.getMax().compareTo(maxOfLeftSubtree) <= 0) { // Right subtree (1) not relevant
                    node.expand0(node.getValue());
                } else {
                    node.expand(node.getValue() / 2, node.getValue() / 2);
                }
            }
        });

        List<WeightedPrefix> mergedMeasurements = new ArrayList<>();
        tree.traversePostOrder((node, prefix) -> {
            if (node.isLeaf()) {
                mergedMeasurements.add(new WeightedPrefix(prefix, node.getValue()));
            }
        });
        return mergedMeasurements;
    }

//    public static void shit(List<WeightedPrefix> measurements, Config config) {
//
//        Objects.requireNonNull(measurements);
//
//        double total = measurements.stream()
//                .mapToDouble(WeightedPrefix::getWeight)
//                .sum();
//
//        PrefixTrie<Double> tree = PrefixTrie.empty(IPUtil.base(config.getClientRange()), 0D);
//        Queue<Measurement> measurementsInPreOrder = new PriorityQueue<>(comparing(Measurement::getPrefix));
//        measurementsInPreOrder.addAll(measurements);
//
//        // Expand tree, fill in measurements, and propagate estimates down
//        tree.traversePreOrder((node, prefix) -> {
//            Measurement nextMeasurement = measurementsInPreOrder.peek();
//            while (nextMeasurement != null && Objects.equals(prefix, nextMeasurement.getPrefix())) {
//                node.setValue(node.getValue() + (total == 0 ? 0 : nextMeasurement.getBytes() / total));
//                measurementsInPreOrder.remove();
//                nextMeasurement = measurementsInPreOrder.peek();
//            }
//            if (nextMeasurement != null && prefix.contains(nextMeasurement.getPrefix().getValue())) {
//                IPv4Address minOfRightSubtree = IPUtil.min(IPUtil.subprefix1(prefix));
//                IPv4Address maxOfLeftSubtree = IPUtil.max(IPUtil.subprefix0(prefix));
//                if (minOfRightSubtree.compareTo(config.getClientRange().getMin()) <= 0) { // Left subtree (0) not relevant
//                    node.expand1(node.getValue());
//                } else if (config.getClientRange().getMax().compareTo(maxOfLeftSubtree) <= 0) { // Right subtree (1) not relevant
//                    node.expand0(node.getValue());
//                } else {
//                    node.expand(node.getValue() / 2, node.getValue() / 2);
//                }
//            }
//        });
//        // Propagate measurements up
//        tree.traversePostOrder((node, prefix) -> {
//            if (!node.isLeaf()) {
//                double val = 0;
//                if (node.getChild0() != null) {
//                    val += node.getChild0().getValue();
//                }
//                if (node.getChild1() != null) {
//                    val += node.getChild1().getValue();
//                }
//                node.setValue(val);
//            }
//        });
//        return tree;
//    }
}
