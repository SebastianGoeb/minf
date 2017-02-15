package net.floodlightcontroller.proactiveloadbalancer;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

public class IPUtils {
    public static IPv4Address max (IPv4AddressWithMask prefix) {
        return prefix.getValue();
    }

    public static IPv4Address min (IPv4AddressWithMask prefix) {
        return prefix.getValue().or(prefix.getMask().not());
    }

    public static IPv4AddressWithMask base(IPv4Address ip, IPv4Address... otherIPs) {
        Objects.requireNonNull(otherIPs);
        for (int i = 32; i >= 0; i--) {
            IPv4AddressWithMask prefix = ip.withMaskOfLength(i);
            boolean containsOtherIPs = true;
            for (IPv4Address otherIP : otherIPs) {
                if (!prefix.contains(otherIP)) {
                    containsOtherIPs = false;
                }
            }
            if (containsOtherIPs) {
                return prefix;
            }
        }
        throw new RuntimeException("Somehow the given IP addresses aren't contained in 0.0.0.0/0, WTF!");
    }

    public static IPv4AddressWithMask subprefix0(IPv4AddressWithMask prefix) {
        return prefix.getValue()
                .withMaskOfLength(prefix.getMask().asCidrMaskLength() + 1);
    }

    public static IPv4AddressWithMask subprefix1(IPv4AddressWithMask prefix) {
        return prefix.getValue().or(prefix.getMask().not())
                .withMaskOfLength(prefix.getMask().asCidrMaskLength() + 1);
    }
}
