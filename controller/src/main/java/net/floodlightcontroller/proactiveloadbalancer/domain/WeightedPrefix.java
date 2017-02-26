package net.floodlightcontroller.proactiveloadbalancer.domain;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

public class WeightedPrefix {
    private IPv4AddressWithMask prefix;
    private double weight;

    public WeightedPrefix(IPv4AddressWithMask prefix, double weight) {
        this.prefix = prefix;
        this.weight = weight;
    }

    public IPv4AddressWithMask getPrefix() {
        return prefix;
    }

    public WeightedPrefix setPrefix(IPv4AddressWithMask prefix) {
        this.prefix = prefix;
        return this;
    }

    public double getWeight() {
        return weight;
    }

    public WeightedPrefix setWeight(double weight) {
        this.weight = weight;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeightedPrefix that = (WeightedPrefix) o;
        return Double.compare(that.weight, weight) == 0 &&
                Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, weight);
    }

    @Override
    public String toString() {
        return "{" + prefix + ": " + weight + "}";
    }
}