package net.floodlightcontroller.proactiveloadbalancer;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.function.BiConsumer;
import java.util.function.Function;

class PrefixTrie<T> {

    private Node<T> root;
    private IPv4AddressWithMask rootPrefix;

    private PrefixTrie(IPv4AddressWithMask rootPrefix, Node<T> root) {
        this.rootPrefix = rootPrefix;
        this.root = root;
    }

    static <T> PrefixTrie<T> copy(PrefixTrie<T> tree) {
        return new PrefixTrie<>(tree.rootPrefix, Node.copy(tree.root));
    }

    static <T> PrefixTrie<T> inflate(IPv4AddressWithMask rootPrefix, T defaultValue, Function<IPv4AddressWithMask, Boolean> shouldExpand) {
        PrefixTrie<T> newTree = new PrefixTrie<>(rootPrefix, Node.with(defaultValue));
        newTree.traversePreOrder((node, prefix) -> {
            if (shouldExpand.apply(prefix)) {
                node.expand(defaultValue, defaultValue);
            }
        });
        return newTree;
    }

    void traversePreOrder(BiConsumer<Node<T>, IPv4AddressWithMask> consumer) {
        root.traverse(rootPrefix, consumer, null, null);
    }

    void traversePostOrder(BiConsumer<Node<T>, IPv4AddressWithMask> consumer) {
        root.traverse(rootPrefix, null, null, consumer);
    }

    Node<T> getRoot() {
        return root;
    }

    // Value class
    static final class Node<T> {
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

        Node<T> getParent() {
            return parent;
        }

        Node<T> getChild0() {
            return child0;
        }

        Node<T> getChild1() {
            return child1;
        }

        T getValue() {
            return value;
        }

        Node<T> setValue(T value) {
            this.value = value;
            return this;
        }

        boolean isLeaf() {
            return child0 == null && child1 == null;
        }

        boolean isRoot() {
            return parent == null;
        }

        void expand(T value0, T value1) {
            child0 = Node.with(value);
            child0.parent = this;
            child1 = Node.with(value);
            child1.parent = this;
        }

        void collapse() {
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
                int mask = prefix.getMask().getInt();
                child0.traverse(IPv4Address.of(prefix.getValue().getInt()).withMask(IPv4Address.of(mask >> 1)), preOrderVisitor, inOrderVisitor, postOrderVisitor);
            }
            if (inOrderVisitor != null) {
                inOrderVisitor.accept(this, prefix);
            }
            if (child1 != null) {
                int mask = prefix.getMask().getInt();
                child0.traverse(IPv4Address.of(prefix.getValue().getInt() | ~mask).withMask(IPv4Address.of(mask >> 1)), preOrderVisitor, inOrderVisitor, postOrderVisitor);
            }
            if (postOrderVisitor != null) {
                postOrderVisitor.accept(this, prefix);
            }
        }
    }
}
