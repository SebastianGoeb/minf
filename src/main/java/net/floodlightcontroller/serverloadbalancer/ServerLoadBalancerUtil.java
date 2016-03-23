package net.floodlightcontroller.serverloadbalancer;

import com.google.common.util.concurrent.AtomicDouble;
import net.floodlightcontroller.serverloadbalancer.assignment.AssignmentTree;
import net.floodlightcontroller.serverloadbalancer.assignment.AssignmentTree.Changes;
import net.floodlightcontroller.serverloadbalancer.assignment.Assignment;
import net.floodlightcontroller.serverloadbalancer.assignment.MaskAssignment;
import net.floodlightcontroller.serverloadbalancer.network.ForwardingTarget;
import net.floodlightcontroller.serverloadbalancer.network.LoadBalanceTarget;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ServerLoadBalancerUtil {
    public static AssignmentTree generateIPv4AssignmentTree(List<? extends LoadBalanceTarget> targets, int maxPrefixLength) {
        // Adjust and normalize weights
        List<NormalizedWeight> normalizedWeights = normalizeWeights(targets, (1 << maxPrefixLength) / 8 * 7);

        // Convert weights into mask assignments
        List<MaskAssignment> maskAssignments = generateMasks(normalizedWeights, maxPrefixLength);

        // Construct priority queue
        PriorityQueue<MaskAssignment> queue = new PriorityQueue<>((a0, a1) -> a0.getMask().compareTo(a1.getMask()));
        queue.addAll(maskAssignments);


        // Assign masks to tree
        AssignmentTree tree = new AssignmentTree();
        while (!queue.isEmpty()) {
            MaskAssignment assignment = queue.poll();
            IPv4Address mask = assignment.getMask();
            ForwardingTarget target = assignment.getTarget();
            // Assign any prefix of relevant size to target
            Changes changes = tree.assignAnyPrefix(mask, target);
            // Add any deleted assignments back to queue
            for (Assignment deletion : changes.deletions) {
                queue.offer(new MaskAssignment(deletion.getPrefix().getMask(), deletion.getTarget()));
            }
        }

        return tree;
    }

    public static AssignmentTree generateIPv4AssignmentTree(List<? extends LoadBalanceTarget> targets, int maxPrefixLength, AssignmentTree oldTree) {
        AssignmentTree tree = new AssignmentTree();

        // Adjust and normalize weights
        List<NormalizedWeight> normalizedWeights = normalizeWeights(targets, (1 << maxPrefixLength) / 8 * 7);

        // Convert weights into mask assignments
        List<MaskAssignment> maskAssignments = generateMasks(normalizedWeights, maxPrefixLength);

        // Get mask assignments that are also present in old tree
        oldTree.assignments().stream()
                .filter(assignment -> maskAssignments.remove(
                        new MaskAssignment(
                                assignment.getPrefix().getMask(),
                                assignment.getTarget()
                        )))
                .forEach(assignment -> tree.assignPrefix(assignment.getPrefix(), assignment.getTarget()));

        // Construct priority queue
        PriorityQueue<MaskAssignment> queue = new PriorityQueue<>((a0, a1) -> a0.getMask().compareTo(a1.getMask()));
        queue.addAll(maskAssignments);


        // Assign masks to tree
        while (!queue.isEmpty()) {
            MaskAssignment assignment = queue.poll();
            IPv4Address mask = assignment.getMask();
            ForwardingTarget target = assignment.getTarget();
            // Assign any prefix of relevant size to target
            Changes changes = tree.assignAnyPrefix(mask, target);
            // Add any deleted assignments back to queue
            for (Assignment deletion : changes.deletions) {
                queue.offer(new MaskAssignment(deletion.getPrefix().getMask(), deletion.getTarget()));
            }
        }

        return tree;
    }

    static List<NormalizedWeight> normalizeWeights(List<? extends LoadBalanceTarget> targets, int max) {
        // Cumulative sum
        AtomicDouble atomicSum = new AtomicDouble(0);
        List<Double> cumsum = targets.stream()
                .map(LoadBalanceTarget::getWeight)
                .map(atomicSum::addAndGet)
                .collect(Collectors.toList());
        double sum = atomicSum.get();

        // Normalize cumulative sum
        List<Integer> normalizedCumsum = cumsum.stream()
                .map(value -> (int) Math.round(value * max / sum))
                .collect(Collectors.toList());

        // Un-cumulative
        List<NormalizedWeight> normalizedWeights = new ArrayList<>(targets.size());
        int prev = 0;
        for  (int i = 0; i < targets.size(); i++) {
            int next = normalizedCumsum.get(i);
            LoadBalanceTarget target = targets.get(i);
            normalizedWeights.add(new NormalizedWeight(next - prev, target));
            prev = next;
        }
        return normalizedWeights;
    }

    static List<MaskAssignment> generateMasks(List<NormalizedWeight> normalizedWeights, int maxMaskLength) {
        Function<NormalizedWeight, List<MaskAssignment>> extractMaskAssignments = weight -> IntStream
                .rangeClosed(0, maxMaskLength)
                .filter(maskLength -> ((1 << (maxMaskLength - maskLength)) & weight.weight) != 0)
                .mapToObj(IPv4Address::ofCidrMaskLength)
                .map(mask -> new MaskAssignment(mask, weight.target))
                .collect(Collectors.toList());

        return normalizedWeights.stream()
                .map(extractMaskAssignments)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    static class NormalizedWeight {
        private int weight;
        private LoadBalanceTarget target;

        public NormalizedWeight(int weight, LoadBalanceTarget target) {
            this.weight = weight;
            this.target = target;
        }
    }
}
