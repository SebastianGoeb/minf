package net.floodlightcontroller.serverloadbalancer;

import com.google.common.primitives.UnsignedInts;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.*;

public class AssignmentTree {
    AssignmentTree parent;
    final IPv4AddressWithMask prefix;
    Integer server;
    AssignmentTree[] children;

    public AssignmentTree() {
        this(null, IPv4AddressWithMask.of("0.0.0.0/0"));
    }

    public AssignmentTree(AssignmentTree parent, IPv4AddressWithMask prefix) {
        this.parent = parent;
        this.prefix = prefix;
    }

    public static AssignmentTree balancedTree(int cidrMaskLength) {
        AssignmentTree tree = new AssignmentTree();
        tree.expandUntil(IPv4Address.ofCidrMaskLength(cidrMaskLength));
        return tree;
    }

    public List<AssignmentWithMask> assignments() {
        List<AssignmentWithMask> assignments = new ArrayList<>();

        if (server != null) {
            assignments.add(new AssignmentWithMask(prefix, server));
        } else if (children != null) {
            assignments.addAll(children[0].assignments());
            assignments.addAll(children[1].assignments());
        }

        return assignments;
    }

    private long assignedAddresses() {
        if (server == null) {
            if (children == null) {
                return 0;
            } else {
                return children[0].assignedAddresses() + children[1].assignedAddresses();
            }
        } else if (parent == null) {
            return 1L << 32;
        } else {
            return UnsignedInts.toLong(parent.prefix.getMask().getInt() ^ prefix.getMask().getInt());
        }
    }

    private void expand() {
        IPv4Address childMask = IPv4Address.of(prefix.getMask().getInt() >> 1 | 1 << 31);
        IPv4Address nextBit = IPv4Address.of(prefix.getMask().getInt() ^ childMask.getInt());
        children = new AssignmentTree[2];
        children[0] = new AssignmentTree(this, prefix.getValue().withMask(childMask));
        children[1] = new AssignmentTree(this, prefix.getValue().or(nextBit).withMask(childMask)); // add 1-bit
        // TODO propagate things?
    }

    public void expandUntil(IPv4Address mask) {
        if (!prefix.getMask().equals(mask)) {
            if (children == null) {
                expand();
            }
            children[0].expandUntil(mask);
            children[1].expandUntil(mask);
        }
    }

    private long freeAddresses() {
        long total = parent == null ? 1L << 32 : UnsignedInts.toLong(parent.prefix.getMask().getInt() ^ prefix.getMask().getInt());
        return total - assignedAddresses();
    }

    public void assignPrefix(IPv4AddressWithMask prefix, Integer server) {
        if (this.prefix.getMask().equals(prefix.getMask())) {
            // We are exactly at prefix (assuming we've chosen the correct path down the tree previously)
            this.server = server;

        } else if (children != null) {
            if (children[0].prefix.matches(prefix.getValue())) {
                // We are higher up the tree than prefix because our mask is less specific
                children[0].assignPrefix(prefix, server);

            } else if (children[1].prefix.matches(prefix.getValue())) {
                // We are higher up the tree than prefix because our mask is less specific
                children[1].assignPrefix(prefix, server);

            } else {
                // We chose the wrong path somewhere?
                throw new IllegalArgumentException("Can't find that prefix here. " + prefix);
            }
        }
    }

    public IPv4AddressWithMask getFreePrefix(IPv4Address mask) {
        if (prefix.getMask().equals(mask)) {
            return prefix;
        } else if (prefix.getMask().compareTo(mask) < 1) {
            long wanted = UnsignedInts.toLong((mask.getInt() << 1) ^ mask.getInt());
            if (children[0].freeAddresses() >= wanted) {
                return children[0].getFreePrefix(mask);
            } else if (children[1].freeAddresses() >= wanted) {
                return children[1].getFreePrefix(mask);
            } else {
                throw new IllegalArgumentException("No free prefixes left? Mask: " + mask);
            }
        } else {
            throw new IllegalArgumentException("We skipped a level. Non-CIDR masks? Mask: " + mask);
        }
    }

    public TreeMap<IPv4Address, Map<Integer, IPv4AddressWithMask>> assignMasks (SortedMap<IPv4Address, List<Integer>> masks) {
        TreeMap<IPv4Address, Map<Integer, IPv4AddressWithMask>> prefixes = new TreeMap<>();
        for (IPv4Address mask : masks.keySet()) {
            TreeMap<Integer, IPv4AddressWithMask> map = new TreeMap<>();
            prefixes.put(mask, map);
            for (Integer server : masks.get(mask)) {
                IPv4AddressWithMask prefix = getFreePrefix(mask);
                assignPrefix(prefix, server);
                map.put(server, prefix);
            }
        }

        return prefixes;
    }

    /**
     * Given a mask, finds the matching subtree that has the least amount of IP space assigned to it
     * @param mask      The mask of the subtree we are searching for
     * @return          The subtree that has the least amount of IP space assigned to it
     */
    public AssignmentTree leastAssignedSubtree(IPv4Address mask) {
        if (prefix.getMask().compareTo(mask) == 0) {
            // We are at the same size prefix as specified
            // If prefix doesn't contain 224.0.0.0/3 (Multicast + RFC6890)
            if (!(prefix.contains(IPv4Address.of("224.0.0.0")) && prefix.getMask().compareTo(IPv4Address.ofCidrMaskLength(3)) <= 0)) {
                return this;
            }
        } else if (prefix.getMask().compareTo(mask) < 0) {
            // We are at a larger prefix than specified

            if (server != null) {
                // This subtree already has an assignment, so it and its subtrees will be completely assigned and thus not considered
                return null;
            }

            AssignmentTree leftTree = children[0].leastAssignedSubtree(mask);
            AssignmentTree rightTree = children[1].leastAssignedSubtree(mask);
            if (leftTree == null && rightTree == null) {
                return null;
            } else if (leftTree == null) {
                return rightTree;
            } else if (rightTree == null) {
                return leftTree;
            } else if (leftTree.assignedAddresses() <= rightTree.assignedAddresses()) {
                return leftTree;
            } else {
                return rightTree;
            }
        } else {
            // We are at a smaller prefix than specified, which shouldn't happen
            throw new IllegalStateException("Somehow we were iterating down the tree and skipped a level.");
        }
        return null;
    }

    public List<AssignmentWithMask> clear() {
        List<AssignmentWithMask> clearedAssignments = new ArrayList<>();

        if (server != null) {
            clearedAssignments.add(new AssignmentWithMask(prefix, server));
            server = null;
        } else if (children != null) {
            clearedAssignments.addAll(children[0].clear());
            clearedAssignments.addAll(children[1].clear());
        }

        return clearedAssignments;
    }

    public int unsafeBalancedDepth() {
        if (children == null) {
            return Integer.bitCount(prefix.getMask().getInt());
        } else {
            return children[0].unsafeBalancedDepth();
        }
    }

    public List<AssignmentTree> getSubtreesAssignedTo(Integer server) {
        List<AssignmentTree> list = new ArrayList<>();
        if (this.server == server) {
            list.add(this);
        } else if (children != null) {
            list.addAll(children[0].getSubtreesAssignedTo(server));
            list.addAll(children[1].getSubtreesAssignedTo(server));
        }
        return list;
    }

    @Override
    public String toString() {
        
        return "";
    }
}
