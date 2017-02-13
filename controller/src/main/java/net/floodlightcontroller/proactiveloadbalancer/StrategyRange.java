package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Objects;

class StrategyRange {

    private IPv4Address min;
    private IPv4Address max;
    private Strategy strategy;

    public StrategyRange(
            @JsonProperty("min")
            @JsonSerialize(using = ToStringSerializer.class)
            @JsonDeserialize(using = IPv4AddressDeserializer.class)
                    IPv4Address min,
            @JsonProperty("max")
            @JsonSerialize(using = ToStringSerializer.class)
            @JsonDeserialize(using = IPv4AddressDeserializer.class)
                    IPv4Address max,
            @JsonProperty("strategy")
                    Strategy strategy) {
        Objects.requireNonNull(min);
        Objects.requireNonNull(max);
        Objects.requireNonNull(strategy);
        this.min = min;
        this.max = max;
        this.strategy = strategy;
    }

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
