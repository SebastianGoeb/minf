package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Objects;

class Host {

    private IPv4Address dip;
    private double weight;

    @JsonCreator
    public Host(
            @JsonProperty("dip")
            @JsonSerialize(using = ToStringSerializer.class)
            @JsonDeserialize(using = IPv4AddressDeserializer.class)
                    IPv4Address dip,
            @JsonProperty("weight")
                    double weight) {
        Objects.requireNonNull(dip);
        Objects.requireNonNull(weight);
        this.dip = dip;
        this.weight = weight;
    }

    public IPv4Address getDip() {
        return dip;
    }

    public double getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Host host = (Host) o;
        return Objects.equals(dip, host.dip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dip);
    }
}
