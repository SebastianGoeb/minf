package net.floodlightcontroller.proactiveloadbalancer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

public class Measurement {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    private final IPv4AddressWithMask prefix;

    @JsonProperty
    private final long bytes;

    public Measurement(IPv4AddressWithMask prefix, long bytes) {
        this.prefix = prefix;
        this.bytes = bytes;
    }

    public Measurement(Flow flow) {
        this(flow.getPrefix(), flow.getBytes());
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
        if (!Objects.equals(m0.prefix, m1.prefix)) {
            throw new IllegalArgumentException("Prefixes must be equal");
        }
        return new Measurement(m0.prefix, m0.bytes + m1.bytes);
    }
}