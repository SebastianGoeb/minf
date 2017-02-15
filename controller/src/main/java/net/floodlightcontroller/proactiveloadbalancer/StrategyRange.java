package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Objects;

class StrategyRange {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = IPv4AddressDeserializer.class)
    private IPv4Address min;

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = IPv4AddressDeserializer.class)
    private IPv4Address max;

    @JsonProperty
    private Strategy strategy;

    public IPv4Address getMin() {
        return min;
    }

    public IPv4Address getMax() {
        return max;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StrategyRange that = (StrategyRange) o;
        return Objects.equals(min, that.min) &&
                Objects.equals(max, that.max) &&
                strategy == that.strategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, strategy);
    }
}
