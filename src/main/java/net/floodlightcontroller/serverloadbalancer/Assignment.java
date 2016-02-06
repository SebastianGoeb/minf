package net.floodlightcontroller.serverloadbalancer;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

public class Assignment {
    private Integer server;
    private IPv4AddressWithMask prefix;

    public Assignment(Integer server, IPv4AddressWithMask prefix) {
        this.server = server;
        this.prefix = prefix;
    }

    public Integer getServer() {
        return server;
    }

    public Assignment setServer(Integer server) {
        this.server = server;
        return this;
    }

    public IPv4AddressWithMask getPrefix() {
        return prefix;
    }

    public Assignment setPrefix(IPv4AddressWithMask prefix) {
        this.prefix = prefix;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Assignment) {
            return server == ((Assignment) obj).server && prefix.equals(((Assignment) obj).prefix);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("Assignment: %s -> %d", prefix, server);
    }
}
