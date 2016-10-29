package net.floodlightcontroller.serverloadbalancer.assignment;

import net.floodlightcontroller.serverloadbalancer.network.ForwardingTarget;
import net.floodlightcontroller.serverloadbalancer.network.TransitionTarget;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssignmentTree {
    private static List<Assignment> constraints;

    static {
        constraints = new ArrayList<>();
        constraints.add(new Assignment(IPv4AddressWithMask.of("224.0.0.0/4"), ForwardingTarget.NONE)); // Multicast
        constraints.add(new Assignment(IPv4AddressWithMask.of("240.0.0.0/4"), ForwardingTarget.NONE)); // RFC 6890 unused
    }

    private Node root;

    public AssignmentTree() {
        root = new Node(IPv4AddressWithMask.of("0.0.0.0/0"), ForwardingTarget.NONE);
        for (Assignment constraint : constraints) {
            assignPrefix(constraint.getPrefix(), constraint.getTarget());
        }
    }

    public static boolean violatesConstraints(Assignment assignment) {
        for (Assignment constraint : constraints) {
            IPv4Address sharedMaskBits = constraint.getPrefix().getMask()
                    .and(assignment.getPrefix().getMask());
            if (constraint.getPrefix().getValue().withMask(sharedMaskBits)
                    .equals(assignment.getPrefix().getValue().withMask(sharedMaskBits))) {
                if (!constraint.getTarget()
                        .equals(assignment.getTarget())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Changes assignPrefix(IPv4AddressWithMask prefix, ForwardingTarget target) {
        return root.assignPrefix(prefix, target);
    }

    public Changes assignAnyPrefix(IPv4Address mask, ForwardingTarget target) {
        PrefixWithCount prefixWithCount = root.findLeastAssignedPrefix(mask, target);
        if (prefixWithCount == null) {
            throw new NoSuchElementException("There is no legal prefix to assign to this target");
        }

        return assignPrefix(prefixWithCount.prefix, target);
    }

    public Changes transitionTo(AssignmentTree newTree) {
        return root.transitionTo(newTree.root);
    }

    public ForwardingTarget findTarget(IPv4Address address) {
        return root.findTarget(address);
    }

    public List<Assignment> assignments() {
        return root.assignments();
    }

    @Override
    public String toString() {
        return root.toString();
    }

    private static class PrefixWithCount {
        private final IPv4AddressWithMask prefix;
        private final long assignments;

        private PrefixWithCount(IPv4AddressWithMask prefix, long assignments) {
            if (prefix == null) {
                throw new IllegalArgumentException("null is not a valid prefix");
            }
            this.prefix = prefix;
            this.assignments = assignments;
        }
    }

    public static class Changes {
        public final List<Assignment> additions;
        public final List<Assignment> deletions;

        public Changes() {
            this.additions = new ArrayList<>();
            this.deletions = new ArrayList<>();
        }

        public Changes addDeletions(List<Assignment> newDeletions) {
            // New deletions are recorded unless they cancel previous additions
            for (Assignment deletion : newDeletions) {
                if (additions.contains(deletion)) {
                    additions.remove(deletion);
                } else {
                    deletions.add(deletion);
                }
            }
            return this;
        }

        public Changes addAdditions(List<Assignment> newAdditions) {
            // New additions are recorded unless they cancel previous deletions
            for (Assignment addition : newAdditions) {
                if (deletions.contains(addition)) {
                    deletions.remove(addition);
                } else {
                    additions.add(addition);
                }
            }
            return this;
        }

        public Changes add(Changes changes) {
            addDeletions(changes.deletions);
            addAdditions(changes.additions);
            return this;
        }
    }

    private class Node {
        private final IPv4AddressWithMask prefix;
        private ForwardingTarget target;
        private Node[] children;

        private Node(IPv4AddressWithMask prefix, ForwardingTarget target) {
            if (prefix == null) {
                throw new IllegalArgumentException("null is not a valid prefix");
            } else if (target == null) {
                throw new IllegalArgumentException("null is not a valid target");
            }
            this.prefix = prefix;
            this.target = target;
            this.children = null;
        }

        // ----------------------------------------------------------------
        // - Methods with side-effects
        // ----------------------------------------------------------------
        public Changes transitionTo(Node node) {
            Changes changes = new Changes();
            if (children == null && node.children == null) {
                // same depth, direct transition
                if (Objects.equals(target, ForwardingTarget.NONE)) {
                    changes.add(assign(node.target));
                } else if (!Objects.equals(target, node.target)) {
                    changes.add(assign(new TransitionTarget(target, node.target)));
                }
            } else {
                // If this node is a leaf, expand it
                if (children == null) {
                    changes.add(expand());
                }
                // If other node is a leaf, expand it
                if (node.children == null) {
                    node.expand();
                }
                // Recurse
                changes.add(children[0].transitionTo(node.children[0]));
                changes.add(children[1].transitionTo(node.children[1]));
            }
            return changes;
        }

        private Changes assignPrefix(IPv4AddressWithMask newPrefix, ForwardingTarget newTarget) {
            Changes changes = new Changes();

            if (prefix.equals(newPrefix)) {
                // Update assignment
                changes.add(assign(newTarget));
            } else if (prefix.getMask().compareTo(newPrefix.getMask()) < 0) {
                // expand if this is a leaf node
                if (children == null) {
                    changes.add(expand());
                }
                // recursively update assignment in children
                if (children[0].prefix.contains(newPrefix.getValue())) {
                    changes.add(children[0].assignPrefix(newPrefix, newTarget));
                } else if (children[1].prefix.contains(newPrefix.getValue())) {
                    changes.add(children[1].assignPrefix(newPrefix, newTarget));
                } else {
                    throw new IllegalArgumentException("Prefix could be found nowhere in this tree " + newPrefix);
                }
            } else {
                throw new IllegalArgumentException("Somehow we skipped a subnet mask");
            }

            // collapse node if children share assignment
            if (children != null
                    && children[0].children == null
                    && children[1].children == null
                    && Objects.equals(children[0].target, children[1].target)) {
                changes.add(collapse());
            }

            return changes;
        }

        private Changes expand() {
            if (children != null) {
                throw new IllegalStateException("This node already has children");
            } else if (prefix.getMask().equals(IPv4Address.NO_MASK)) {
                throw new IllegalStateException("Cannot expand a /32 prefix");
            }

            // Record deletions before change
            Changes changes = new Changes();
            changes.addDeletions(assignments());

            // Move target assignment to children
            IPv4Address childMask = IPv4Address.ofCidrMaskLength(prefix.getMask().asCidrMaskLength() + 1);
            IPv4Address nextBit = IPv4Address.of(prefix.getMask().getInt() ^ childMask.getInt());
            children = new Node[]{
                    new Node(prefix.getValue().withMask(childMask), target),
                    new Node(prefix.getValue().or(nextBit).withMask(childMask), target)
            };

            // Unassign this node in the tree
            target = ForwardingTarget.NONE;

            // Record additions after change
            changes.addAdditions(assignments());
            return changes;
        }

        private Changes collapse() {
            if (children == null) {
                throw new IllegalStateException("This node doesn't have children");
            } else if (!Objects.equals(children[0].target, children[1].target)) {
                throw new IllegalStateException("Cannot collapse because children have different replicas assigned");
            }

            return assign(children[0].target); // We know children[0].target == children[1].target
        }

        private Changes assign(ForwardingTarget target) {
            if (target == null) {
                throw new IllegalArgumentException("null is not a valid target");
            }
            // Record deletions before change
            Changes changes = new Changes();
            changes.addDeletions(assignments());

            // Update assignment
            this.target = target;
            children = null;

            // Record additions after change
            changes.addAdditions(assignments());
            return changes;
        }

        // ----------------------------------------------------------------
        // - Methods with no side-effects
        // ----------------------------------------------------------------
        private PrefixWithCount findLeastAssignedPrefix(IPv4Address mask, ForwardingTarget target) {
            PrefixWithCount candidate;
            if (prefix.getMask().equals(mask)) {
                // If at correct depth, return this prefix and how many addresses are assigned to different targets
                candidate = new PrefixWithCount(prefix, addressesAssignedToDifferentTarget(target));
            } else if (prefix.getMask().compareTo(mask) < 0) {
                // If at shallower depth, return best suited child
                if (children != null) {
                    PrefixWithCount left = children[0].findLeastAssignedPrefix(mask, target);
                    PrefixWithCount right = children[1].findLeastAssignedPrefix(mask, target);
                    if (left == null || violatesConstraints(new Assignment(left.prefix, target))) {
                        candidate = right;
                    } else if (right == null || violatesConstraints(new Assignment(right.prefix, target))) {
                        candidate = left;
                    } else {
                        candidate = left.assignments <= right.assignments
                                ? left
                                : right;
                    }
                } else if (this.target == ForwardingTarget.NONE) {
                    candidate = new PrefixWithCount(prefix.getValue().withMask(mask), 0);
                } else {
                    candidate = null;
                }
            } else {
                throw new IllegalStateException("We somehow ended up deeper down the tree than intended.");
            }

            return candidate == null || violatesConstraints(new Assignment(candidate.prefix, target))
                    ? null
                    : candidate;
        }

        private long addressesAssignedToDifferentTarget(ForwardingTarget target) {
            if (children != null) {
                return children[0].addressesAssignedToDifferentTarget(target)
                        + children[1].addressesAssignedToDifferentTarget(target);
            } else if (this.target == ForwardingTarget.NONE || Objects.equals(this.target, target)) {
                return 0;
            } else {
                return 1L << (32 - prefix.getMask().asCidrMaskLength());
            }
        }

        private List<Assignment> assignments() {
            List<Assignment> assignments = new ArrayList<>();

            if (target != ForwardingTarget.NONE) {
                assignments.add(new Assignment(prefix, target));
            } else if (children != null) {
                assignments.addAll(children[0].assignments());
                assignments.addAll(children[1].assignments());
            }

            return assignments;
        }

        private ForwardingTarget findTarget(IPv4Address address) {
            if (children == null) {
                return target;
            } else {
                if (children[0].prefix.contains(address)) {
                    return children[0].findTarget(address);
                } else if (children[1].prefix.contains(address)) {
                    return children[1].findTarget(address);
                } else {
                    throw new IllegalStateException("Neither child node matches prefix " + address);
                }
            }
        }

        @Override
        public String toString() {
            int MIN_GAP = 4;
            String value = String.format("%s -> %s", prefix, target);
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
}