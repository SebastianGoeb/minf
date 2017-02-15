package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Config {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    private IPv4Address vip;
    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    private IPv4AddressWithMask dVipRange;
    @JsonProperty
    private Map<Strategy, IPv4AdressRange> strategyRanges;
    @JsonProperty
    private Topology topology;
    @JsonProperty
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<DatapathId> loadBalancers;
    @JsonProperty
    private long measurementInterval;
    @JsonProperty
    private double measurementThreshold;
    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    private Map<IPv4Address, Double> weights;

    public IPv4Address getVip() {
        return vip;
    }

    public IPv4AddressWithMask getdVipRange() {
        return dVipRange;
    }

    public Map<Strategy, IPv4AdressRange> getStrategyRanges() {
        return strategyRanges;
    }

    public Topology getTopology() {
        return topology;
    }

    public List<DatapathId> getLoadBalancers() {
        return loadBalancers;
    }

    public long getMeasurementInterval() {
        return measurementInterval;
    }

    public double getMeasurementThreshold() {
        return measurementThreshold;
    }

    public Map<IPv4Address, Double> getWeights() {
        return weights;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Config config = (Config) o;
        return measurementInterval == config.measurementInterval &&
                Double.compare(config.measurementThreshold, measurementThreshold) == 0 &&
                Objects.equals(vip, config.vip) &&
                Objects.equals(dVipRange, config.dVipRange) &&
                Objects.equals(strategyRanges, config.strategyRanges) &&
                Objects.equals(topology, config.topology) &&
                Objects.equals(loadBalancers, config.loadBalancers) &&
                Objects.equals(weights, config.weights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vip,
                dVipRange,
                strategyRanges,
                topology,
                loadBalancers,
                measurementInterval,
                measurementThreshold,
                weights);
    }
}
