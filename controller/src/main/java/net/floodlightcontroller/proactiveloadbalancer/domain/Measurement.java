package net.floodlightcontroller.proactiveloadbalancer.domain;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

public class Measurement {
    private final short tableId;
    private final IPv4AddressWithMask prefix;
    private final long bytes;

    public Measurement(short tableId, IPv4AddressWithMask prefix, long bytes) {
        this.tableId = tableId;
        this.prefix = prefix;
        this.bytes = bytes;
    }

    public Measurement(IPv4AddressWithMask prefix, long bytes) {
        this((short) -1, prefix, bytes);
    }

    public short getTableId() {
        return tableId;
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

    public static Measurement add(Measurement m0, Measurement m1) {
        if (m0.tableId != m1.tableId) {
            throw new IllegalArgumentException("TableIds must be equal");
        } else if (!Objects.equals(m0.prefix, m1.prefix)) {
            throw new IllegalArgumentException("Prefixes must be equal");
        }
        return new Measurement(m0.tableId, m0.prefix, m0.bytes + m1.bytes);
    }
}