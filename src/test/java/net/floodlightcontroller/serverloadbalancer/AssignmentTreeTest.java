package net.floodlightcontroller.serverloadbalancer;

import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.*;

import static org.junit.Assert.*;

public class AssignmentTreeTest {

    @Test
    public void testGetFreePrefix() throws Exception {
        int maxPrefixLength = 3;

        // Init tree
        AssignmentTree tree = AssignmentTree.balancedTree(maxPrefixLength);

        // Init weights
        List<Integer> weightsNormalized = ServerLoadBalancerUtil.normalize(Arrays.asList(1.0, 3.0, 4.0), 1 << maxPrefixLength);

        // Init masks
        SortedMap<IPv4Address, List<Integer>> masks = ServerLoadBalancerUtil.generateMasks(weightsNormalized);

        // Init prefixes
        TreeMap<IPv4Address, Map<Integer, IPv4AddressWithMask>> prefixes = tree.assignMasks(masks);

        assertEquals(IPv4AddressWithMask.of("0.0.0.0/1"), prefixes.get(IPv4Address.of("128.0.0.0")).get(2));
        assertEquals(IPv4AddressWithMask.of("128.0.0.0/2"), prefixes.get(IPv4Address.of("192.0.0.0")).get(1));
        assertEquals(IPv4AddressWithMask.of("192.0.0.0/3"), prefixes.get(IPv4Address.of("224.0.0.0")).get(0));
        assertEquals(IPv4AddressWithMask.of("224.0.0.0/3"), prefixes.get(IPv4Address.of("224.0.0.0")).get(1));
    }
}