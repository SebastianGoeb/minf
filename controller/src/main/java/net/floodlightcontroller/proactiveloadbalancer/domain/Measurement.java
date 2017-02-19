package net.floodlightcontroller.proactiveloadbalancer.domain;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

public class Measurement {
    private final IPv4AddressWithMask prefix;
    private final long bytes;

    public Measurement(IPv4AddressWithMask prefix, long bytes) {
        this.prefix = prefix;
        this.bytes = bytes;
    }

    public IPv4AddressWithMask getPrefix() {
        return prefix;
    }

    public long getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return "{" + prefix + ": " + bytes + "B}";
    }
}