package net.floodlightcontroller.serverloadbalancer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.*;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ServerLoadBalancerUtilTest {

    private static double MACHINE_EPS;

    @Before
    public void setUp() throws Exception {
        while (1.0 + 0.5 * MACHINE_EPS != 1.0) {
            MACHINE_EPS *= 0.5;
        }
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testRenormalizeWeightsEmpty() throws Exception {
        // Empty
        List<Double> weights = new ArrayList<>();
        List<Integer> normalized = ServerLoadBalancerUtil.normalize(weights, 2);

        // Check size
        assertTrue(normalized.isEmpty());
    }

    @Test
    public void testRenormalizeWeightsSingle() throws Exception {
        // Some decimal number
        List<Double> weights = Collections.singletonList(3.5);
        List<Integer> normalized = ServerLoadBalancerUtil.normalize(weights, 2);

        // Check size
        assertEquals(1, normalized.size());

        // Check values add up
        assertEquals(2, (int) normalized.get(0));
    }

    @Test
    public void testRenormalizeWeightsRounding() throws Exception {
        // A naive method may incorrectly round all these to 0
        List<Double> weights = Arrays.asList(1.0, 1.0, 1.0);
        List<Integer> normalized = ServerLoadBalancerUtil.normalize(weights, 1);

        // Check size
        assertEquals(3, normalized.size());

        // Check values add up
        int sum = normalized.get(0) + normalized.get(1) + normalized.get(2);
        assertEquals(1, sum);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMasksForEmpty() throws Exception {
        List<Integer> weights = Arrays.asList(new Integer[]{});
        SortedMap<IPv4Address, List<Integer>> prefixes = ServerLoadBalancerUtil.generateMasks(weights);
    }

    @Test
    public void testMasksForSingle() throws Exception {
        // A naive method may incorrectly round all these to 0
        List<Integer> weights = Collections.singletonList(1);
        SortedMap<IPv4Address, List<Integer>> prefixes = ServerLoadBalancerUtil.generateMasks(weights);

        // Check that it contains all relevant prefixes
        assertTrue(prefixes.containsKey(IPv4Address.ofCidrMaskLength(0)));
        // Check that it contains no other prefixes
        assertEquals(1, prefixes.keySet().size());

        // Check that it has all the correct assignments for x.x.x.x/y
        assertEquals(1, prefixes.get(IPv4Address.ofCidrMaskLength(0)).size());

        assertTrue(prefixes.get(IPv4Address.ofCidrMaskLength(0)).contains(0));
    }

    @Test
    public void testGenerateMasks() throws Exception {
        // A naive method may incorrectly round all these to 0
        List<Integer> weights = Arrays.asList(1, 3, 4);
        SortedMap<IPv4Address, List<Integer>> prefixes = ServerLoadBalancerUtil.generateMasks(weights);

        // Check that it contains all relevant prefixes
        assertTrue(prefixes.containsKey(IPv4Address.ofCidrMaskLength(0)));
        assertTrue(prefixes.containsKey(IPv4Address.ofCidrMaskLength(1)));
        assertTrue(prefixes.containsKey(IPv4Address.ofCidrMaskLength(2)));
        assertTrue(prefixes.containsKey(IPv4Address.ofCidrMaskLength(3)));
        // Check that it contains no other prefixes
        assertEquals(4, prefixes.keySet().size());

        // Check that it has all the correct assignments for x.x.x.x/y
        assertEquals(0, prefixes.get(IPv4Address.ofCidrMaskLength(0)).size());
        assertEquals(1, prefixes.get(IPv4Address.ofCidrMaskLength(1)).size());
        assertEquals(1, prefixes.get(IPv4Address.ofCidrMaskLength(2)).size());
        assertEquals(2, prefixes.get(IPv4Address.ofCidrMaskLength(3)).size());

        assertTrue(prefixes.get(IPv4Address.ofCidrMaskLength(1)).contains(2));
        assertTrue(prefixes.get(IPv4Address.ofCidrMaskLength(2)).contains(1));
        assertTrue(prefixes.get(IPv4Address.ofCidrMaskLength(3)).contains(0));
        assertTrue(prefixes.get(IPv4Address.ofCidrMaskLength(3)).contains(1));
    }

    // TODO tests for preservedPrefixes

    @Test
    public void testGenerateTransitions() throws Exception {
        // Source Tree
        AssignmentTree from = AssignmentTree.balancedTree(3);
        from.assignPrefix(IPv4AddressWithMask.of("0.0.0.0/1"), 1);
        from.assignPrefix(IPv4AddressWithMask.of("128.0.0.0/2"), 2);
        from.assignPrefix(IPv4AddressWithMask.of("192.0.0.0/3"), 3);
        from.assignPrefix(IPv4AddressWithMask.of("224.0.0.0/3"), 4);

        // Destination Tree
        AssignmentTree to = AssignmentTree.balancedTree(3);
        to.assignPrefix(IPv4AddressWithMask.of("0.0.0.0/1"), 5);
        to.assignPrefix(IPv4AddressWithMask.of("128.0.0.0/3"), 6);
        to.assignPrefix(IPv4AddressWithMask.of("160.0.0.0/3"), 7);
        to.assignPrefix(IPv4AddressWithMask.of("192.0.0.0/2"), 8);

        // Transitions
        List<Transition> transitions = ServerLoadBalancerUtil.generateTransitions(from, to);

        // Check size
        assertEquals(3, transitions.size());

        // Check contents
        assertThat(transitions.get(0).getFrom().size(), is(1));
        assertThat(transitions.get(0).getFrom(), contains(new Assignment(IPv4AddressWithMask.of("0.0.0.0/1"), 1)));
        assertThat(transitions.get(0).getTo().size(), is(1));
        assertThat(transitions.get(0).getTo(), contains(new Assignment(IPv4AddressWithMask.of("0.0.0.0/1"), 5)));

        assertThat(transitions.get(1).getFrom().size(), is(1));
        assertThat(transitions.get(1).getFrom(), contains(new Assignment(IPv4AddressWithMask.of("128.0.0.0/2"), 2)));
        assertThat(transitions.get(1).getTo().size(), is(2));
        assertThat(transitions.get(1).getTo(), containsInAnyOrder(
                new Assignment(IPv4AddressWithMask.of("128.0.0.0/3"), 6),
                new Assignment(IPv4AddressWithMask.of("160.0.0.0/3"), 7)));

        assertThat(transitions.get(2).getFrom().size(), is(2));
        assertThat(transitions.get(2).getFrom(), containsInAnyOrder(
                new Assignment(IPv4AddressWithMask.of("192.0.0.0/3"), 3),
                new Assignment(IPv4AddressWithMask.of("224.0.0.0/3"), 4)));
        assertThat(transitions.get(2).getTo().size(), is(1));
        assertThat(transitions.get(2).getTo(), contains(
                new Assignment(IPv4AddressWithMask.of("192.0.0.0/2"), 8)));
    }

    @Test
    public void testGenerateAssignmentTreeFewerTransitions() throws Exception {
        AssignmentTree oldTree = AssignmentTree.balancedTree(3);
        oldTree.assignPrefix(IPv4AddressWithMask.of("0.0.0.0/2"), 1);
        oldTree.assignPrefix(IPv4AddressWithMask.of("64.0.0.0/2"), 2);
        oldTree.assignPrefix(IPv4AddressWithMask.of("128.0.0.0/2"), -1);
        oldTree.assignPrefix(IPv4AddressWithMask.of("192.0.0.0/3"), 2);
        oldTree.assignPrefix(IPv4AddressWithMask.of("224.0.0.0/3"), -1);

        Config config = new Config()
                .setWeights(Arrays.asList(4d, 3d))
                .setMaxPrefixLength(3)
                .setCoreSwitch(new SwitchDesc())
                .addServer(Server.create("10.0.0.1", "00:00:00:00:00:01", "p_1", 4))
                .addServer(Server.create("10.0.0.2", "00:00:00:00:00:02", "p_2", 3));
        AssignmentTree newTree = ServerLoadBalancerUtil.generateAssignmentTreeFewerTransitions(config, oldTree);

        assertThat(newTree.children[0].server, is(0));
        assertThat(newTree.children[1].children[0].server, is(1));
        assertThat(newTree.children[1].children[1].children[0].server, is(1));
        assertThat(newTree.children[1].children[1].children[1].server, is(-1));


        config.setWeights(Arrays.asList(3d, 4d));
        newTree = ServerLoadBalancerUtil.generateAssignmentTreeFewerTransitions(config, oldTree);

        assertThat(newTree.children[0].server, is(1));
        assertThat(newTree.children[1].children[0].server, is(0));
        assertThat(newTree.children[1].children[1].children[0].server, is(0));
        assertThat(newTree.children[1].children[1].children[1].server, is(-1));
    }
}