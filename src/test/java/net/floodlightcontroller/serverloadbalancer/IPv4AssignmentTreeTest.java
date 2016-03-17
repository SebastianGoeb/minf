package net.floodlightcontroller.serverloadbalancer;

import net.floodlightcontroller.serverloadbalancer.IPv4AssignmentTree.Changes;
import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class IPv4AssignmentTreeTest {

    @Test
    public void testViolatesConstraints() throws Exception {
        assertThat(IPv4AssignmentTree.violatesConstraints(
                new AssignmentWithMask(IPv4AddressWithMask.of("224.0.0.0/3"), 1)),
                is(true));

        assertThat(IPv4AssignmentTree.violatesConstraints(
                new AssignmentWithMask(IPv4AddressWithMask.of("224.0.0.0/4"), 1)),
                is(true));

        assertThat(IPv4AssignmentTree.violatesConstraints(
                new AssignmentWithMask(IPv4AddressWithMask.of("224.0.0.0/5"), 1)),
                is(true));

        assertThat(IPv4AssignmentTree.violatesConstraints(
                new AssignmentWithMask(IPv4AddressWithMask.of("224.0.0.0/3"), -1)),
                is(false));

        assertThat(IPv4AssignmentTree.violatesConstraints(
                new AssignmentWithMask(IPv4AddressWithMask.of("224.0.0.0/4"), -1)),
                is(false));

        assertThat(IPv4AssignmentTree.violatesConstraints(
                new AssignmentWithMask(IPv4AddressWithMask.of("224.0.0.0/5"), -1)),
                is(false));
    }

    @Test
    public void testNodeLeastAssignedPrefix() throws Exception {
        IPv4AssignmentTree t = new IPv4AssignmentTree();
        t.assignPrefix(IPv4AddressWithMask.of("0.0.0.0/1"), 1);
        Changes changes1 = t.assignAnyPrefix(IPv4Address.ofCidrMaskLength(1), 2);
        Changes changes2 = t.assignAnyPrefix(IPv4Address.ofCidrMaskLength(1), -1);

        assertThat(changes1.additions, contains(new AssignmentWithMask(IPv4AddressWithMask.of("0.0.0.0/1"), 2)));
        assertThat(changes1.deletions, contains(new AssignmentWithMask(IPv4AddressWithMask.of("0.0.0.0/1"), 1)));

        assertThat(changes2.additions, contains(new AssignmentWithMask(IPv4AddressWithMask.of("128.0.0.0/1"), -1)));
    }
}