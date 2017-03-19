package net.floodlightcontroller.proactiveloadbalancer.domain;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.U64;

import java.util.Objects;

public class Flow {
    private final U64 cookie;
    private final short tableId;
    private final IPv4AddressWithMask prefix;
    private final long bytes;

    public Flow(U64 cookie, short tableId, IPv4AddressWithMask prefix, long bytes) {
        this.cookie = cookie;
        this.tableId = tableId;
        this.prefix = prefix;
        this.bytes = bytes;
    }

    public U64 getCookie() {
        return cookie;
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
}