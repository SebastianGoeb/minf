package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.List;
import java.util.Objects;

public class Config {

    private IPv4Address vip;
    private List<StrategyRange> strategyRanges;
    private Topology topology;

    @JsonCreator
    public Config(
            @JsonProperty("vip")
            @JsonSerialize(using = ToStringSerializer.class)
            @JsonDeserialize(using = IPv4AddressDeserializer.class)
                    IPv4Address vip,
            @JsonProperty("strategyRanges")
                    List<StrategyRange> strategyRanges,
            @JsonProperty("topology")
                    Topology topology) {
        Objects.requireNonNull(vip);
        Objects.requireNonNull(strategyRanges);
        Objects.requireNonNull(topology);
        this.vip = vip;
        this.strategyRanges = strategyRanges;
        this.topology = topology;
    }

    public IPv4Address getVip() {
        return vip;
    }

    public List<StrategyRange> getStrategyRanges() {
        return strategyRanges;
    }

    public Topology getTopology() {
        return topology;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Config config = (Config) o;
        return Objects.equals(vip, config.vip) &&
                Objects.equals(strategyRanges, config.strategyRanges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vip, strategyRanges);
    }
}
