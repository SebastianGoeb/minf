package net.floodlightcontroller.serverloadbalancer;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IPv4AssignmentTree {
    private static List<AssignmentWithMask> constraints;
    private Node root;

    static {
        constraints = new ArrayList<>();
        constraints.add(new AssignmentWithMask(IPv4AddressWithMask.of("224.0.0.0/4"), -1)); // Multicast
        constraints.add(new AssignmentWithMask(IPv4AddressWithMask.of("240.0.0.0/4"), -1)); // RFC 6890 unused
    }

    public IPv4AssignmentTree() {
        root = new Node(null, IPv4AddressWithMask.of("0.0.0.0/0"), -1);
    }

    public static boolean violatesConstraints(AssignmentWithMask assignment) {
        for (AssignmentWithMask constraint : constraints) {
            IPv4Address sharedMaskBits = constraint.getPrefix().getMask()
                    .and(assignment.getPrefix().getMask());
            if (constraint.getPrefix().getValue().withMask(sharedMaskBits)
                    .equals(assignment.getPrefix().getValue().withMask(sharedMaskBits))) {
                if (!constraint.getServer()
                        .equals(assignment.getServer())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Changes assignPrefix(IPv4AddressWithMask prefix, Integer server) {
        return root.assignPrefix(prefix, server);
    }

    public Changes assignAnyPrefix(IPv4Address mask, Integer server) {
        PrefixWithCount prefixWithCount = root.findLeastAssignedPrefix(mask, server);
        if (prefixWithCount == null) {
            throw new NoSuchElementException("There is no legal prefix to assign to this server");
        }

        return assignPrefix(prefixWithCount.prefix, server);
    }

    @Override
    public String toString() {
        return root.toString();
    }

    private class Node {
        private Node parent;
        private IPv4AddressWithMask prefix;
        private Integer server;
        private Node[] children;

        private Node(Node parent, IPv4AddressWithMask prefix, Integer server) {
            this.parent = parent;
            this.prefix = prefix;
            this.server = server;
            this.children = null;
        }

        // non-safe
        private Changes assignPrefix(IPv4AddressWithMask newPrefix, Integer newServer) {
            Changes changes = new Changes();

            if (prefix.equals(newPrefix)) {
                // Update assignment
                changes.add(assign(newServer));
            } else if (prefix.getMask().compareTo(newPrefix.getMask()) < 0) {
                // expand if this is a leaf node
                if (children == null) {
                    changes.add(expand());
                }
                // recursively update assignment in children
                if (children[0].prefix.contains(newPrefix.getValue())) {
                    changes.add(children[0].assignPrefix(newPrefix, newServer));
                } else if (children[1].prefix.contains(newPrefix.getValue())) {
                    changes.add(children[1].assignPrefix(newPrefix, newServer));
                } else {
                    throw new IllegalArgumentException("Prefix could be found nowhere in this tree " + newPrefix);
                }
            } else {
                throw new IllegalArgumentException("Somehow we skipped a subnet mask");
            }

            // collapse node if children share assignment
            if (children != null && children[0].server.equals(children[1].server)) {
                changes.add(collapse());
            }

            return changes;
        }

        // non-safe
        private Changes assign(Integer server) {
            // Record deletions before change
            Changes changes = new Changes();
            changes.deletions.addAll(assignments());

            // Update assignment
            this.server = server; // We know children[0].server == children[1].server
            children = null;

            // Record additions after change
            changes.additions.addAll(assignments());
            return changes;
        }

        // non-safe
        private Changes expand() {
            if (children != null) {
                throw new IllegalStateException("This node already has children");
            } else if (prefix.getMask().equals(IPv4Address.NO_MASK)) {
                throw new IllegalStateException("Cannot expand a /32 prefix");
            }

            // Record deletions before change
            Changes changes = new Changes();
            changes.deletions.addAll(assignments());

            // Move server assignment to children
            IPv4Address childMask = IPv4Address.ofCidrMaskLength(prefix.getMask().asCidrMaskLength() + 1);
            IPv4Address nextBit = IPv4Address.of(prefix.getMask().getInt() ^ childMask.getInt());
            children = new Node[]{
                    new Node(this, prefix.getValue().withMask(childMask), server),
                    new Node(this, prefix.getValue().or(nextBit).withMask(childMask), server)
            };

            // Unassign this node in the tree
            server = null;

            // Record additions after change
            changes.additions.addAll(assignments());
            return changes;
        }

        // non-safe
        private Changes collapse() {
            if (children == null) {
                throw new IllegalStateException("This node doesn't have children");
            } else if (!children[0].server.equals(children[1].server)) {
                throw new IllegalStateException("Cannot collapse because children have different replicas assigned");
            }

            return assign(children[0].server);
        }

        private PrefixWithCount findLeastAssignedPrefix(IPv4Address mask, Integer server) {
            PrefixWithCount candidate;
            if (prefix.getMask().equals(mask)) {
                // If at correct depth, return this prefix and how many addresses are assigned to different servers
                candidate = new PrefixWithCount(prefix, addressesAssignedToDifferentServers(server));
            } else if (prefix.getMask().compareTo(mask) < 0) {
                // If at shallower depth, return best suited child
                if (children != null) {
                    PrefixWithCount left = children[0].findLeastAssignedPrefix(mask, server);
                    PrefixWithCount right = children[1].findLeastAssignedPrefix(mask, server);
                    if (left == null || violatesConstraints(new AssignmentWithMask(left.prefix, server))) {
                        candidate = right;
                    } else if (right == null || violatesConstraints(new AssignmentWithMask(left.prefix, server))) {
                        candidate = left;
                    } else {
                        candidate = left.assignments <= right.assignments
                                ? left
                                : right;
                    }
                } else if (this.server == null || this.server.equals(server) || this.server.equals(-1)) {
                    candidate = new PrefixWithCount(prefix.getValue().withMask(mask), 0);
                } else {
                    candidate = new PrefixWithCount(prefix.getValue().withMask(mask), 1L << (32 - mask.asCidrMaskLength()));
                }
            } else {
                throw new IllegalStateException("We somehow ended up deeper down the tree than intended.");
            }

            return violatesConstraints(new AssignmentWithMask(candidate.prefix, server))
                    ? null
                    : candidate;
        }

        private long addressesAssignedToDifferentServers(Integer server) {
            if (children != null) {
                return children[0].addressesAssignedToDifferentServers(server)
                        + children[1].addressesAssignedToDifferentServers(server);
            } else if (this.server == null || this.server.equals(server) || this.server.equals(-1)) {
                return 0;
            } else {
                return 1L << (32 - prefix.getMask().asCidrMaskLength());
            }
        }

        private List<AssignmentWithMask> assignments() {
            List<AssignmentWithMask> assignments = new ArrayList<>();

            if (server != null) {
                assignments.add(new AssignmentWithMask(prefix, server));
            } else if (children != null) {
                assignments.addAll(children[0].assignments());
                assignments.addAll(children[1].assignments());
            }

            return assignments;
        }

        @Override
        public String toString() {
            int MIN_GAP = 4;
            String value = String.format("%s -> %s", prefix, server);
            if (children == null) {
                return value;
            }

            String[] stringsLeft = children[0].toString().split("\n");
            String[] stringsRight = children[1].toString().split("\n");

            // Split first lines into [padding, term, padding]
            Pattern p = Pattern.compile("( *)(.+)( *)");
            Matcher leftMatcher = p.matcher(stringsLeft[0]);
            Matcher rightMatcher = p.matcher(stringsRight[0]);
            leftMatcher.matches();
            rightMatcher.matches();

            // Calculate positions of various anchor points
            int gap = Math.max(MIN_GAP, value.length() - stringsLeft[0].length() - stringsRight[0].length());
            int width = stringsLeft[0].length() + gap + stringsRight[0].length();
            int leftLineIdx = leftMatcher.group(1).length() + leftMatcher.group(2).length() / 2;
            int rightLineIdx = width - (rightMatcher.group(3).length() + rightMatcher.group(2).length() / 2) - 1;
            int parentLineIdx = (leftLineIdx + rightLineIdx) / 2;
            int valueIdx = Math.max(0, Math.min(parentLineIdx - value.length() / 2, width - value.length()));

            StringBuilder sb = new StringBuilder();

            // Line 1
            int leftSpaces = valueIdx;
            int rightSpaces = width - leftSpaces - value.length();
            sb.append(new String(new char[leftSpaces]).replace('\0', ' '));
            sb.append(value);
            sb.append(new String(new char[rightSpaces]).replace('\0', ' '));
            sb.append("\n");

            // Line 2
            leftSpaces = leftLineIdx + 1;
            rightSpaces = width - rightLineIdx;
            int leftUnderscores = parentLineIdx - leftLineIdx - 1;
            int rightUnderscores = rightLineIdx - parentLineIdx - 1;
            sb.append(new String(new char[leftSpaces]).replace('\0', ' '));
            sb.append(new String(new char[leftUnderscores]).replace('\0', '_'));
            sb.append("|");
            sb.append(new String(new char[rightUnderscores]).replace('\0', '_'));
            sb.append(new String(new char[rightSpaces]).replace('\0', ' '));
            sb.append("\n");

            // Line 3
            sb.append(new String(new char[leftSpaces - 1]).replace('\0', ' '));
            sb.append("/");
            sb.append(new String(new char[leftUnderscores + 1 + rightUnderscores]).replace('\0', ' '));
            sb.append("\\");
            sb.append(new String(new char[rightSpaces - 1]).replace('\0', ' '));
            sb.append("\n");

            // Remaining lines
            for (int i = 0; i < Math.max(stringsLeft.length, stringsRight.length); i++) {
                sb.append(i < stringsLeft.length
                        ? stringsLeft[i]
                        : new String(new char[stringsLeft[0].length()]).replace('\0', ' '));
                sb.append(new String(new char[gap]).replace('\0', ' '));
                sb.append(i < stringsRight.length
                        ? stringsRight[i]
                        : new String(new char[stringsRight[0].length()]).replace('\0', ' '));
                sb.append("\n");
            }

            return sb.toString();
        }
    }

    private class PrefixWithCount {
        private final IPv4AddressWithMask prefix;
        private final long assignments;

        private PrefixWithCount(IPv4AddressWithMask prefix, long assignments) {
            this.prefix = prefix;
            this.assignments = assignments;
        }
    }

    public class Changes {
        public final List<AssignmentWithMask> additions;
        public final List<AssignmentWithMask> deletions;

        private Changes() {
            this.additions = new ArrayList<>();
            this.deletions = new ArrayList<>();
        }

        private void add(Changes changes) {
            // New additions are recorded unless they cancel previous deletions
            for (AssignmentWithMask addition : changes.additions) {
                if (deletions.contains(addition)) {
                    deletions.remove(addition);
                } else {
                    additions.add(addition);
                }
            }

            // New deletions are recorded unless they cancel previous additions
            for (AssignmentWithMask deletion : changes.deletions) {
                if (additions.contains(deletion)) {
                    additions.remove(deletion);
                } else {
                    deletions.add(deletion);
                }
            }
        }
    }
}