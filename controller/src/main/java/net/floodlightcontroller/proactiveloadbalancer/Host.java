package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Objects;

class Host {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = IPv4AddressDeserializer.class)
    private IPv4Address dip;

    @JsonProperty
    private double weight;

    Host() {
        dip = IPv4Address.NONE;
        weight = 1;
    }

    IPv4Address getDip() {
        return dip;
    }

    double getWeight() {
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
