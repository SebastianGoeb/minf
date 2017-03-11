package net.floodlightcontroller.proactiveloadbalancer;


import net.floodlightcontroller.proactiveloadbalancer.domain.LoadBalancingFlow;
import net.floodlightcontroller.proactiveloadbalancer.domain.Transition;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static java.util.Comparator.comparing;

class DifferenceFinder {
    static boolean prefixesAreContiguous(List<LoadBalancingFlow> sorted) {
        if (sorted.size() <= 1) {
            return true;
        }
        for (int i = 0; i < sorted.size() - 1; i++){
            IPv4AddressWithMask prev = sorted.get(i).getPrefix();
            IPv4AddressWithMask next = sorted.get(i + 1).getPrefix();
            int maxPrev = prev.getValue().or(prev.getMask().not()).getInt();
            int minNext = next.getValue().getInt();
            if (minNext - maxPrev != 1) {
                return false;
            }
        }
        return true;
    }

    static boolean prefixesCoverSameRange(List<LoadBalancingFlow> sortedOld, List<LoadBalancingFlow> sortedNew) {
        if (sortedOld.isEmpty() && sortedNew.isEmpty()) {
            return true;
        } else if (sortedOld.isEmpty() || sortedNew.isEmpty()) {
            return false;
        }

        IPv4Address minOld = sortedOld.get(0).getPrefix().getValue();
        IPv4Address maxOld = sortedOld.get(sortedOld.size() - 1).getPrefix().getSubnetBroadcastAddress();
        IPv4Address minNew = sortedNew.get(0).getPrefix().getValue();
        IPv4Address maxNew = sortedNew.get(sortedNew.size() - 1).getPrefix().getSubnetBroadcastAddress();
        return minOld.equals(minNew) && maxOld.equals(maxNew);
    }

    static List<Transition> transitions(List<LoadBalancingFlow> flowsOld, List<LoadBalancingFlow> flowsNew) {
        List<LoadBalancingFlow> sortedOld = sortFlows(flowsOld);
        List<LoadBalancingFlow> sortedNew = sortFlows(flowsNew);

        if (!prefixesAreContiguous(sortedOld) || !prefixesAreContiguous(sortedNew)) {
            throw new IllegalArgumentException("Prefixes must be contiguous");
        }

        if (!prefixesCoverSameRange(sortedOld, sortedNew)) {
            throw new IllegalArgumentException("Prefixes must cover same range");
        }

        Iterator<LoadBalancingFlow> iterOld = sortedOld.iterator();
        Iterator<LoadBalancingFlow> iterNew = sortedNew.iterator();
        LoadBalancingFlow flowOld = iterOld.hasNext() ? iterOld.next() : null;
        LoadBalancingFlow flowNew = iterNew.hasNext() ? iterNew.next() : null;
        List<Transition> transitions = new ArrayList<>();

        while (flowOld != null && flowNew != null) {
            IPv4AddressWithMask prefixOld = flowOld.getPrefix();
            IPv4AddressWithMask prefixNew = flowNew.getPrefix();

            if (prefixOld.getMask().compareTo(prefixNew.getMask()) >= 0) {
                transitions.add(new Transition(prefixOld, flowOld.getDip(), flowNew.getDip()));
            } else {
                transitions.add(new Transition(prefixNew, flowOld.getDip(), flowNew.getDip()));
            }

            IPv4Address maxOld = prefixOld.getSubnetBroadcastAddress();
            IPv4Address maxNew = prefixNew.getSubnetBroadcastAddress();
            if (maxOld.compareTo(maxNew) <= 0) {
                flowOld = iterOld.hasNext() ? iterOld.next() : null;
            }
            if (maxNew.compareTo(maxOld) <= 0) {
                flowNew = iterNew.hasNext() ? iterNew.next() : null;
            }
        }
        return transitions;
    }

    /**
     * Finds prefixes that should be routed to controller during transition
     * @param flowsOld list of flows before transition
     * @param flowsNew list of flows after transition
     * @return list of prefixes to route to controller along with old and new IP
     */
    static List<Transition> findDifferences(List<LoadBalancingFlow> flowsOld,
            List<LoadBalancingFlow> flowsNew) {
        List<Transition> transitions = new ArrayList<>();
        for (Transition transition : transitions(flowsOld, flowsNew)) {
            if (requiresController(transition)) {
                transitions.add(transition);
            }
        }
        return transitions;
    }

    // Helper
    private static boolean requiresController(Transition transition) {
        boolean oldExists = transition.getIpOld() != null;
        boolean newIsDifferent = !Objects.equals(transition.getIpOld(), transition.getIpNew());
        return oldExists && newIsDifferent;
    }

    // Helper
    private static List<LoadBalancingFlow> sortFlows(List<LoadBalancingFlow> flows) {
        List<LoadBalancingFlow> sorted = new ArrayList<>(flows);
        sorted.sort(comparing(LoadBalancingFlow::getPrefix));
        return sorted;
    }
}
