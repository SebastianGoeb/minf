package net.floodlightcontroller.serverloadbalancer.assignment;

import net.floodlightcontroller.serverloadbalancer.network.ForwardingTarget;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Objects;

public class MaskAssignment {
    private final IPv4Address mask;
    private final ForwardingTarget target;

    public MaskAssignment(IPv4Address mask, ForwardingTarget target) {
        if (mask == null) {
            throw new IllegalArgumentException(String.format("Cannot assign null to %s", target));
        } else if (target == null) {
            throw new IllegalArgumentException(String.format("Cannot assign /%d to null", mask.asCidrMaskLength()));
        }
        this.mask = mask;
        this.target = target;
    }

    public IPv4Address getMask() {
        return mask;
    }

    public ForwardingTarget getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
                || (obj != null
                && obj instanceof MaskAssignment
                && Objects.equals(mask, ((MaskAssignment) obj).mask)
                && Objects.equals(target, ((MaskAssignment) obj).target));
    }

    @Override
    public String toString() {
        return String.format("MaskAssignment: %s -> %s", mask, target);
    }
}
