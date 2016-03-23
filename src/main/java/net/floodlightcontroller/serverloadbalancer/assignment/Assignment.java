package net.floodlightcontroller.serverloadbalancer.assignment;

import net.floodlightcontroller.serverloadbalancer.network.ForwardingTarget;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

public class Assignment {
    private final IPv4AddressWithMask prefix;
    private final ForwardingTarget target;

    public Assignment(IPv4AddressWithMask prefix, ForwardingTarget target) {
        if (prefix == null || target == null) {
            throw new IllegalArgumentException(String.format("Cannot assign %s to %s.", prefix, target));
        }
        this.prefix = prefix;
        this.target = target;
    }

    public IPv4AddressWithMask getPrefix() {
        return prefix;
    }

    public ForwardingTarget getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
                || (obj != null
                && obj instanceof Assignment
                && Objects.equals(prefix, ((Assignment) obj).prefix)
                && Objects.equals(target, ((Assignment) obj).target));
    }

    @Override
    public String toString() {
        return String.format("assignment: %s -> %s", prefix, target);
    }
}
