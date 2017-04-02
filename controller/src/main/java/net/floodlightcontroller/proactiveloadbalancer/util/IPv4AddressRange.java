package net.floodlightcontroller.proactiveloadbalancer.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.floodlightcontroller.proactiveloadbalancer.serializer.IPv4AddressDeserializer;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

public class IPv4AddressRange {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = IPv4AddressDeserializer.class)
    private IPv4Address min;

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = IPv4AddressDeserializer.class)
    private IPv4Address max;

    public IPv4AddressRange() {
        this(null, null);
    }

    public IPv4AddressRange(String s) {
        String[] parts = s.split("-");
        this.min = IPv4Address.of(parts[0]);
        this.max = IPv4Address.of(parts[1]);
    }

    public IPv4AddressRange(IPv4Address min, IPv4Address max) {
        this.min = min;
        this.max = max;
    }

    public static IPv4AddressRange of(IPv4Address min, IPv4Address max) {
        return new IPv4AddressRange(min, max);
    }

    public static IPv4AddressRange of(IPv4AddressWithMask prefix) {
        IPv4Address min = prefix.getValue();
        IPv4Address max = prefix.getValue().or(prefix.getMask().not());
        return of(min, max);
    }

    public IPv4Address getMin() {
        return min;
    }

    public IPv4AddressRange setMin(IPv4Address min) {
        this.min = min;
        return this;
    }

    public IPv4Address getMax() {
        return max;
    }

    public IPv4AddressRange setMax(IPv4Address max) {
        this.max = max;
        return this;
    }

    public boolean contains(IPv4Address ip) {
        return min.compareTo(ip) <= 0 && ip.compareTo(max) <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IPv4AddressRange that = (IPv4AddressRange) o;
        return Objects.equals(min, that.min) &&
                Objects.equals(max, that.max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    @Override
    public String toString() {
        return String.format("%s-%s", min, max);
    }
}
