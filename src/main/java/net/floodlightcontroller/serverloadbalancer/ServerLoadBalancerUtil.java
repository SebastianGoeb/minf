package net.floodlightcontroller.serverloadbalancer;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.*;
import java.util.stream.Collectors;

public class ServerLoadBalancerUtil {

    /**
     * Generate the ideal assignment for a given configuration
     * @param config The desired load balancer configuration
     * @return The assignment of servers to IPv4-prefixes
     */
    public static AssignmentTree generateAssignmentTreeOptimal(Config config) {
        AssignmentTree assignmentTree = AssignmentTree.balancedTree(config.getMaxPrefixLength());

        // Drop multicast and RFC6890 prefixes ( 224.0.0.0/4 + 240.0.0.0/4 = 224.0.0.0/3 )
        IPv4AddressWithMask unused = IPv4AddressWithMask.of("224.0.0.0/3");
        assignmentTree.assignPrefix(unused, -1);

        List<Integer> ids = config.getServers().keySet().stream()
                .collect(Collectors.toList());
        List<Double> weights = config.getServers().values().stream()
                .map(server -> server.getWeight())
                .collect(Collectors.toList());

        // Assign everything to "drop" if we have no servers registered
        if (ids.size() == 0) {
            ids.add(-1);
            weights.add(1d);
        }

        // Scale and round weights to add up to maxPrefixLength * 7/8
        List<Integer> normalizedWeights = normalize(weights, (1 << config.getMaxPrefixLength()) / 8 * 7);

        // Translate weights into subnet masks that can be assigned to prefixes, servers grouped and sorted by mask
        SortedMap<IPv4Address, List<Integer>> masks = new TreeMap<>();
        for (int i = 0; i < normalizedWeights.size(); i++) {
            for (int j = 0; j <= config.getMaxPrefixLength(); j++) {
                if ((normalizedWeights.get(i) & (1 << j)) != 0) {
                    IPv4Address mask = IPv4Address.ofCidrMaskLength((config.getMaxPrefixLength() - j));
                    if (!masks.containsKey(mask)) {
                        masks.put(mask, new ArrayList<>());
                    }
                    masks.get(mask).add(ids.get(i));
                }
            }
        }

        // Assign prefixes
        // TODO refactor
        assignmentTree.assignMasks(masks);

        return assignmentTree;
    }

    public static AssignmentTree generateAssignmentTreeFewerTransitions(Config config, AssignmentTree oldTree) {
        int oldDepth = oldTree.unsafeBalancedDepth();
        AssignmentTree newTree = AssignmentTree.balancedTree(config.getMaxPrefixLength());
        if (oldDepth < config.getMaxPrefixLength()) {
            oldTree.expandUntil(IPv4Address.ofCidrMaskLength(config.getMaxPrefixLength()));
        } else if (oldDepth > config.getMaxPrefixLength()) {
            newTree.expandUntil(IPv4Address.ofCidrMaskLength(oldDepth));
        }

        // Drop multicast and RFC6890 prefixes ( 224.0.0.0/4 + 240.0.0.0/4 = 224.0.0.0/3 )
        IPv4AddressWithMask unused = IPv4AddressWithMask.of("224.0.0.0/3");
        newTree.assignPrefix(unused, -1);

        List<Integer> ids = config.getServers().keySet().stream()
                .collect(Collectors.toList());
        List<Double> weights = config.getServers().values().stream()
                .map(server -> server.getWeight())
                .collect(Collectors.toList());

        // Assign everything to "drop" if we have no servers registered
        if (ids.size() == 0) {
            ids.add(-1);
            weights.add(1d);
        }

        // Scale and round weights to add up to maxPrefixLength * 7/8
        List<Integer> normalizedWeights = normalize(weights, (1 << config.getMaxPrefixLength()) / 8 * 7);

        // Translate weights into subnet masks that can be assigned to prefixes, servers grouped and sorted by mask
        SortedMap<IPv4Address, List<Integer>> masks = new TreeMap<>();
        for (int i = 0; i < normalizedWeights.size(); i++) {
            for (int j = 0; j <= config.getMaxPrefixLength(); j++) {
                if ((normalizedWeights.get(i) & (1 << j)) != 0) {
                    IPv4Address mask = IPv4Address.ofCidrMaskLength((config.getMaxPrefixLength() - j));
                    if (!masks.containsKey(mask)) {
                        masks.put(mask, new ArrayList<>());
                    }
                    masks.get(mask).add(ids.get(i));
                }
            }
        }

        // Pre-assign shared assignments
        List<AssignmentWithMask> oldAssignments = oldTree.assignments();
        for (AssignmentWithMask assignment : oldAssignments) {
            IPv4AddressWithMask prefix = assignment.getPrefix();
            IPv4Address mask = assignment.getPrefix().getMask();
            Integer server = assignment.getServer();

            if (masks.containsKey(mask) && masks.get(mask).contains(server)) {
                // This assignment is already present, so pre-assign it and remove it from masks
                newTree.assignPrefix(prefix, server);
                masks.get(mask).remove(server);
            }
        }

        // Assign all remaining prefixes (and those kicked back out in the process)
        for (Map.Entry<IPv4Address, List<Integer>> masksEntry : masks.entrySet()) {
            IPv4Address mask = masksEntry.getKey();

            for (Integer server : masksEntry.getValue()) {
                // Find least-cost assignment in new tree
                AssignmentTree leastCostSubtree = newTree.leastAssignedSubtree(mask);
                List<AssignmentWithMask> clearedAssignments = leastCostSubtree.clear();

                // Clear out previous assignments to make room for new assignment, but add them back to masks/server list
                for (AssignmentWithMask assignment : clearedAssignments) {
                    IPv4Address clearedMask = assignment.getPrefix().getMask();
                    Integer clearedServer = assignment.getServer();
                    if (!masks.containsKey(clearedMask)) {
                        masks.put(clearedMask, new ArrayList<>());
                    }
                    masks.get(clearedMask).add(clearedServer);
                    // TODO is this necessary?
                    // masks.get(clearedMask).sort(Comparator.naturalOrder());
                }

                newTree.assignPrefix(leastCostSubtree.prefix, server);
            }
        }

        return newTree;
    }

    /**
     * Generate transitions to go from one assignment to another
     * @param from The current assignment
     * @param to The next assignment
     * @return The list of transitions to execute
     */
    public static List<Transition> generateTransitions(AssignmentTree from, AssignmentTree to) {
        List<Transition> transitions = new ArrayList<>();
        if (from.server != null && to.server != null) {
            // Both trees have an assignment, so we must be dealing with a direct transition
            if (from.server != to.server) {
                transitions.add(Transition.direct(from.server, from.prefix, to.server, to.prefix));
            }

        } else if (from.server == null && to.server == null) {
            // Neither tree has an assignment, so we continue searching deeper
            if (from.children != null && to.children != null) {
                transitions.addAll(generateTransitions(from.children[0], to.children[0]));
                transitions.addAll(generateTransitions(from.children[1], to.children[1]));
            }

        } else {
            Transition transition;
            if (from.server != null) {
                // Only our source tree has an assignment, so we must be dealing with a split or unassignment
                transition = Transition.split(from.server, from.prefix);
                transitions.add(transition);

            } else {
                // Only our destination tree has an assignment, so we must be dealing with a merge or assignment
                transition = Transition.merge(to.server, to.prefix);
                transitions.add(transition);
            }

            // Search deeper for children
            if (from.children != null && to.children != null) {
                addTransitionChildren(from.children[0], to.children[0], transition);
                addTransitionChildren(from.children[1], to.children[1], transition);
            }
        }

        // Return what we have found
        return transitions;
    }

    static void addTransitionChildren(AssignmentTree fromTree, AssignmentTree toTree, Transition parentTransition) {
        if (fromTree.server != null) {
            // If our source has an assignment, add it to the existing merge
            parentTransition.addFrom(fromTree.server, fromTree.prefix);

        } else if (toTree.server != null) {
            // If our destination has an assignment, add it to the existing split
            parentTransition.addTo(toTree.server, toTree.prefix);

        } else if (fromTree.children != null && toTree.children != null) {
            // If neither tree has any assignment, continue searching deeper
            addTransitionChildren(fromTree.children[0], toTree.children[0], parentTransition);
            addTransitionChildren(fromTree.children[1], toTree.children[1], parentTransition);
        }
    }

    static List<Integer> normalize(List<Double> weights, int max) {
        // Cumulative sum
        List<Double> cumsum = new ArrayList<>(weights.size());
        double sum = 0;
        for (double value : weights) {
            sum += value;
            cumsum.add(sum);
        }

        // Normalize cumulative sum
        List<Integer> normalizedCumsum = new ArrayList<>(weights.size());
        for (Double value : cumsum) {
            normalizedCumsum.add((int) Math.round(value * max / sum));
        }

        // Un-cumulative
        List<Integer> normalized = new ArrayList<>(weights.size());
        int prev = 0;
        for (Integer next : normalizedCumsum) {
            normalized.add(next - prev);
            prev = next;
        }
        return normalized;
    }

    static SortedMap<IPv4Address, List<Integer>> generateMasks(List<Integer> weights) {
        // Sum of weights
        int sum = 0;
        for (Integer weight : weights) {
            sum += weight;
        }

        // Sum must be positive
        if (sum <= 0) {
            throw new IllegalArgumentException("Sum of weights must be positive");
        }

        // Largest possible subnet mask for these weights ( log2(sum), that many 1-bit in MSB position )
        int maxMask = Integer.reverse(sum - 1); // log2(sum) 1-bits in mask (256
        int offset = Integer.bitCount(~maxMask); // Number of 0-bits in mask

        // Group (mask, server) pairs by mask
        SortedMap<IPv4Address, List<Integer>> masks = new TreeMap<>();

        // For each mask size, record all servers that will be assigned such a mask
        for (int mask = maxMask; mask != 0; mask <<= 1) {
            List<Integer> affectedServers = new ArrayList<>();
            masks.put(IPv4Address.of(mask), affectedServers);

            for (int i = 0; i < weights.size(); i++) {
                if (((mask ^ (mask << 1)) & (weights.get(i) << offset)) != 0) {
                    // weight has least significant bit of mask set to 1, i.e. needs a prefix of this size
                    affectedServers.add(i);
                }
            }
        }

        // If anything claims 0.0.0.0 mask, assign it
        List<Integer> affectedServers = new ArrayList<>();
        masks.put(IPv4Address.of(0), affectedServers);
        for (int i = 0; i < weights.size(); i++) {
            if (weights.get(i) == sum) {
                affectedServers.add(i);
            }
        }
        return masks;
    }

    static SortedMap<IPv4Address, List<Integer>> preservedMasks(SortedMap<IPv4Address, List<Integer>> masks, SortedMap<IPv4Address, List<Integer>> newMasks) {
        SortedMap<IPv4Address, List<Integer>> commonMasks = new TreeMap<>();
        for (IPv4Address mask : masks.keySet()) {
            // Intersection of old and new servers for this mask
            List<Integer> servers = masks.get(mask);
            List<Integer> newservers = newMasks.get(mask);
            List<Integer> commonservers = new ArrayList<>(servers);
            commonservers.retainAll(newservers);
            commonMasks.put(mask, commonservers);
        }
        return commonMasks;
    }
}
