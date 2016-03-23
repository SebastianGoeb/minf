package net.floodlightcontroller.serverloadbalancer;

import net.floodlightcontroller.serverloadbalancer.assignment.AssignmentTree;
import net.floodlightcontroller.serverloadbalancer.assignment.AssignmentTree.Changes;
import net.floodlightcontroller.serverloadbalancer.assignment.Assignment;
import net.floodlightcontroller.serverloadbalancer.network.ForwardingTarget;
import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class AssignmentTreeTest {

    @Test
    public void testViolatesConstraints() throws Exception {
        ForwardingTarget ft1 = new ForwardingTarget() {
        };

        assertThat(AssignmentTree.violatesConstraints(
                new Assignment(IPv4AddressWithMask.of("224.0.0.0/3"), ft1)),
                is(true));

        assertThat(AssignmentTree.violatesConstraints(
                new Assignment(IPv4AddressWithMask.of("224.0.0.0/4"), ft1)),
                is(true));

        assertThat(AssignmentTree.violatesConstraints(
                new Assignment(IPv4AddressWithMask.of("224.0.0.0/5"), ft1)),
                is(true));

        assertThat(AssignmentTree.violatesConstraints(
                new Assignment(IPv4AddressWithMask.of("224.0.0.0/3"), ForwardingTarget.NONE)),
                is(false));

        assertThat(AssignmentTree.violatesConstraints(
                new Assignment(IPv4AddressWithMask.of("224.0.0.0/4"), ForwardingTarget.NONE)),
                is(false));

        assertThat(AssignmentTree.violatesConstraints(
                new Assignment(IPv4AddressWithMask.of("224.0.0.0/5"), ForwardingTarget.NONE)),
                is(false));
    }

    @Test
    public void testNodeLeastAssignedPrefix() throws Exception {
        ForwardingTarget ft1 = new ForwardingTarget() {
        };
        ForwardingTarget ft2 = new ForwardingTarget() {
        };
        AssignmentTree t = new AssignmentTree();
        t.assignPrefix(IPv4AddressWithMask.of("0.0.0.0/1"), ft1);
        Changes changes1 = t.assignAnyPrefix(IPv4Address.ofCidrMaskLength(1), ft2);

        assertThat(changes1.additions, contains(new Assignment(IPv4AddressWithMask.of("0.0.0.0/1"), ft2)));
        assertThat(changes1.deletions, contains(new Assignment(IPv4AddressWithMask.of("0.0.0.0/1"), ft1)));
    }

    @Test
    public void testIPv4AssignmentTreeConstructor() throws Exception {
        AssignmentTree t = new AssignmentTree();
    }

    @Test
    public void testTransitionTo() throws Exception {
        ForwardingTarget ft1 = new ForwardingTarget() {
            @Override
            public String toString() {
                return "1";
            }
        };
        ForwardingTarget ft2 = new ForwardingTarget() {
            @Override
            public String toString() {
                return "2";
            }
        };

        AssignmentTree t1 = new AssignmentTree();
        t1.assignPrefix(IPv4AddressWithMask.of("0.0.0.0/1"), ft1);
        t1.assignPrefix(IPv4AddressWithMask.of("128.0.0.0/3"), ft2);
        t1.assignPrefix(IPv4AddressWithMask.of("160.0.0.0/3"), ft1);
        t1.assignPrefix(IPv4AddressWithMask.of("192.0.0.0/3"), ft2);


        AssignmentTree t2 = new AssignmentTree();
        t2.assignPrefix(IPv4AddressWithMask.of("0.0.0.0/2"), ft1);
        t2.assignPrefix(IPv4AddressWithMask.of("64.0.0.0/2"), ft2);
        t2.assignPrefix(IPv4AddressWithMask.of("128.0.0.0/2"), ft1);
        t2.assignPrefix(IPv4AddressWithMask.of("192.0.0.0/3"), ft1);

        System.out.println(t1);
        Changes changes = t1.transitionTo(t2);

        System.out.println(t1);
        System.out.println("Deletions");
        changes.deletions.forEach(System.out::println);
        System.out.println("Additions");
        changes.additions.forEach(System.out::println);
    }
}