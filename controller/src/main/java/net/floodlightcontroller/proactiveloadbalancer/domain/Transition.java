package net.floodlightcontroller.proactiveloadbalancer.domain;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.text.MessageFormat;
import java.util.Objects;

public class Transition {
    private IPv4AddressWithMask prefix;
    private IPv4Address ipOld;
    private IPv4Address ipNew;

    public Transition(IPv4AddressWithMask prefix, IPv4Address ipOld, IPv4Address ipNew) {
        this.prefix = prefix;
        this.ipOld = ipOld;
        this.ipNew = ipNew;
    }

    public IPv4AddressWithMask getPrefix() {
        return prefix;
    }

    public Transition setPrefix(IPv4AddressWithMask prefix) {
        this.prefix = prefix;
        return this;
    }

    public IPv4Address getIpOld() {
        return ipOld;
    }

    public Transition setIpOld(IPv4Address ipOld) {
        this.ipOld = ipOld;
        return this;
    }

    public IPv4Address getIpNew() {
        return ipNew;
    }

    public Transition setIpNew(IPv4Address ipNew) {
        this.ipNew = ipNew;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transition that = (Transition) o;
        return Objects.equals(prefix, that.prefix) &&
                Objects.equals(ipOld, that.ipOld) &&
                Objects.equals(ipNew, that.ipNew);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, ipOld, ipNew);
    }

    @Override
    public String toString() {
        return MessageFormat.format("Transition[{0}: {1} -> {2}]", prefix, ipOld, ipNew);
    }
}
