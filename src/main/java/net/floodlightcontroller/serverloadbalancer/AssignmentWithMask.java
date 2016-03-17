package net.floodlightcontroller.serverloadbalancer;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

public class AssignmentWithMask {
    private IPv4AddressWithMask prefix;
    private Integer server;

    public AssignmentWithMask(IPv4AddressWithMask prefix, Integer server) {
        this.prefix = prefix;
        this.server = server;
    }

    public IPv4AddressWithMask getPrefix() {
        return prefix;
    }

    public AssignmentWithMask setPrefix(IPv4AddressWithMask prefix) {
        this.prefix = prefix;
        return this;
    }

    public Integer getServer() {
        return server;
    }

    public AssignmentWithMask setServer(Integer server) {
        this.server = server;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AssignmentWithMask) {
            return server.equals(((AssignmentWithMask) obj).server) && prefix.equals(((AssignmentWithMask) obj).prefix);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("Assignment: %s -> %d", prefix, server);
    }
}
