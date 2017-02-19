package net.floodlightcontroller.proactiveloadbalancer.util;

import com.google.common.primitives.UnsignedInts;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.*;

public class IPUtil {
    public static IPv4Address max (IPv4AddressWithMask prefix) {
        return prefix.getValue();
    }

    public static IPv4Address min (IPv4AddressWithMask prefix) {
        return prefix.getValue().or(prefix.getMask().not());
    }

    public static IPv4AddressWithMask base(IPv4AddressRange range) {
        return base(range.getMin(), range.getMax());
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

    public static List<IPv4AddressWithMask> nonOverlappingPrefixes(IPv4AddressRange range) {
        int rangeMin = range.getMin().getInt();
        int rangeMax = range.getMax().getInt();
        List<IPv4AddressWithMask> prefixes = new ArrayList<>();
        Queue<IPv4AddressWithMask> queue = new PriorityQueue<>();
        queue.add(IPv4AddressWithMask.of("0.0.0.0/0"));
        while(!queue.isEmpty()) {
            IPv4AddressWithMask prefix = queue.poll();
            int min = prefix.getValue().getInt();
            int max = prefix.getValue().or(prefix.getMask().not()).getInt();
            boolean inside = UnsignedInts.compare(rangeMin, min) <= 0 && UnsignedInts.compare(max, rangeMax) <= 0;
            boolean outside = UnsignedInts.compare(max, rangeMin) < 0 || UnsignedInts.compare(rangeMax, min) < 0;
            if (inside) {
                // Contained, save
                prefixes.add(prefix);
            } else if (!outside) {
                // Too large, split
                queue.add(IPv4Address.of(min).withMaskOfLength(prefix.getMask().asCidrMaskLength() + 1));
                queue.add(IPv4Address.of(max).withMaskOfLength(prefix.getMask().asCidrMaskLength() + 1));
            }
        }
        return prefixes;
    }
}
