package net.floodlightcontroller.proactiveloadbalancer.util;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Collection;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.BiConsumer;

public class PrefixTrie<T> {

    private Node<T> root;
    private IPv4AddressWithMask rootPrefix;

    private PrefixTrie(IPv4AddressWithMask rootPrefix, Node<T> root) {
        this.rootPrefix = rootPrefix;
        this.root = root;
    }

    public static <T> PrefixTrie<T> empty(IPv4AddressWithMask rootPrefix, T defaultValue) {
        return new PrefixTrie<>(rootPrefix, Node.with(defaultValue));
    }

    public static <T> PrefixTrie<T> copy(PrefixTrie<T> tree) {
        return new PrefixTrie<>(tree.rootPrefix, Node.copy(tree.root));
    }

    public static <T> PrefixTrie<T> inflate(IPv4AddressWithMask rootPrefix, T defaultValue, Collection<IPv4AddressWithMask> prefixes) {
        // Sorted by prefix (low to high, then shallow to deep)
        Queue<IPv4AddressWithMask> prefixesInPreOrder = new PriorityQueue<>(prefixes);
        PrefixTrie<T> newTree = empty(rootPrefix, defaultValue);
        // traverse in pre-order, expand relevant nodes until all prefixes are covered
        newTree.traversePreOrder((node, currentPrefix) -> {
            // Remove prefix for current depth, no longer required.
            IPv4AddressWithMask nextPrefix = prefixesInPreOrder.peek();
            while (currentPrefix.equals(nextPrefix)) {
                prefixesInPreOrder.remove();
                nextPrefix = prefixesInPreOrder.peek();
            }
            // If next prefix is further down this subtree, continue expanding.
            if (nextPrefix != null && currentPrefix.contains(nextPrefix.getValue())) {
                node.expand(defaultValue, defaultValue);
            }
        });
        return newTree;
    }

    public void traversePreOrder(BiConsumer<Node<T>, IPv4AddressWithMask> consumer) {
        root.traverse(rootPrefix, consumer, null, null);
    }

    public void traversePostOrder(BiConsumer<Node<T>, IPv4AddressWithMask> consumer) {
        root.traverse(rootPrefix, null, null, consumer);
    }

    public Node<T> getRoot() {
        return root;
    }

    // Value class
    public static final class Node<T> {
        private Node<T> parent;
        private Node<T> child0;
        private Node<T> child1;
        private T value;

        private Node() {
        }

        private static <T> Node<T> with(T a) {
            return new Node<T>().setValue(a);
        }

        private static <T> Node<T> copy(Node<T> node) {
            Node<T> newNode = Node.with(node.value);
            if (node.child0 != null) {
                newNode.child0 = Node.copy(node.child0);
                newNode.child0.parent = newNode;
            }
            if (node.child1 != null) {
                newNode.child1 = Node.copy(node.child1);
                newNode.child1.parent = newNode;
            }
            return newNode;
        }

        public Node<T> getParent() {
            return parent;
        }

        public Node<T> getChild0() {
            return child0;
        }

        public Node<T> getChild1() {
            return child1;
        }

        public T getValue() {
            return value;
        }

        public Node<T> setValue(T value) {
            this.value = value;
            return this;
        }

        public boolean isLeaf() {
            return child0 == null && child1 == null;
        }

        public boolean isRoot() {
            return parent == null;
        }

        public void expand(T value0, T value1) {
            expand0(value0);
            expand1(value1);
        }

        public void expand0(T value0) {
            child0 = Node.with(value0);
            child0.parent = this;
        }

        public void expand1(T value1) {
            child1 = Node.with(value1);
            child1.parent = this;
        }

        public void collapse() {
            child0.parent = null;
            child0 = null;
            child1.parent = null;
            child1 = null;
        }

        private void traverse(IPv4AddressWithMask prefix,
                BiConsumer<Node<T>, IPv4AddressWithMask> preOrderVisitor,
                BiConsumer<Node<T>, IPv4AddressWithMask> inOrderVisitor,
                BiConsumer<Node<T>, IPv4AddressWithMask> postOrderVisitor) {
            if (preOrderVisitor != null) {
                preOrderVisitor.accept(this, prefix);
            }
            if (child0 != null) {
                child0.traverse(IPUtil.subprefix0(prefix), preOrderVisitor, inOrderVisitor, postOrderVisitor);
            }
            if (inOrderVisitor != null) {
                inOrderVisitor.accept(this, prefix);
            }
            if (child1 != null) {
                child1.traverse(IPUtil.subprefix1(prefix), preOrderVisitor, inOrderVisitor, postOrderVisitor);
            }
            if (postOrderVisitor != null) {
                postOrderVisitor.accept(this, prefix);
            }
        }
    }
}
