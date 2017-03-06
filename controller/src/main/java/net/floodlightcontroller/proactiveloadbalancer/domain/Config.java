package net.floodlightcontroller.proactiveloadbalancer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.floodlightcontroller.proactiveloadbalancer.serializer.*;
import net.floodlightcontroller.proactiveloadbalancer.util.IPv4AddressRange;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Config {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = IPv4AddressDeserializer.class)
    private IPv4Address vip;

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = IPv4AddressWithMaskDeserializer.class)
    private IPv4AddressWithMask dVipRange;

    @JsonProperty
    private Map<Strategy, IPv4AddressRange> strategyRanges;

    // Derived
    private IPv4AddressRange clientRange;

    @JsonProperty
    private Topology topology;

    @JsonProperty
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @JsonDeserialize(contentUsing = DatapathIdDeserializer.class)
    private List<DatapathId> loadBalancers;

    @JsonProperty
    private long measurementInterval;

    @JsonProperty
    private double measurementThreshold;

    @JsonProperty
    private boolean ignoreMeasurements;

    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    @JsonDeserialize(keyUsing = IPv4AddressKeyDeserializer.class)
    private Map<IPv4Address, Double> weights;

    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    @JsonDeserialize(keyUsing = DatapathIdKeyDeserializer.class)
    private Map<DatapathId, MeasurementCommand> measurementCommands;

    public IPv4Address getVip() {
        return vip;
    }

    public Config setVip(IPv4Address vip) {
        this.vip = vip;
        return this;
    }

    public IPv4AddressWithMask getdVipRange() {
        return dVipRange;
    }

    public Config setdVipRange(IPv4AddressWithMask dVipRange) {
        this.dVipRange = dVipRange;
        return this;
    }

    public Map<Strategy, IPv4AddressRange> getStrategyRanges() {
        return strategyRanges;
    }

    public Config setStrategyRanges(Map<Strategy, IPv4AddressRange> strategyRanges) {
        this.strategyRanges = strategyRanges;
        this.clientRange = strategyRanges.values().stream()
                .reduce((r0, r1) -> IPv4AddressRange.of(
                        r0.getMin().compareTo(r1.getMin()) < 0 ? r0.getMin() : r1.getMin(),
                        r0.getMax().compareTo(r1.getMax()) > 0 ? r0.getMax() : r1.getMax()
                ))
                .orElse(null);
        return this;
    }

    public IPv4AddressRange getClientRange() {
        return clientRange;
    }

    public Topology getTopology() {
        return topology;
    }

    public Config setTopology(Topology topology) {
        this.topology = topology;
        return this;
    }

    public List<DatapathId> getLoadBalancers() {
        return loadBalancers;
    }

    public Config setLoadBalancers(List<DatapathId> loadBalancers) {
        this.loadBalancers = loadBalancers;
        return this;
    }

    public long getMeasurementInterval() {
        return measurementInterval;
    }

    public Config setMeasurementInterval(long measurementInterval) {
        this.measurementInterval = measurementInterval;
        return this;
    }

    public double getMeasurementThreshold() {
        return measurementThreshold;
    }

    public Config setMeasurementThreshold(double measurementThreshold) {
        this.measurementThreshold = measurementThreshold;
        return this;
    }

    public boolean isIgnoreMeasurements() {
        return ignoreMeasurements;
    }

    public Config setIgnoreMeasurements(boolean ignoreMeasurements) {
        this.ignoreMeasurements = ignoreMeasurements;
        return this;
    }

    public Map<IPv4Address, Double> getWeights() {
        return weights;
    }

    public Config setWeights(Map<IPv4Address, Double> weights) {
        this.weights = weights;
        return this;
    }

    public Map<DatapathId, MeasurementCommand> getMeasurementCommands() {
        return measurementCommands;
    }

    public Config setMeasurementCommands(Map<DatapathId, MeasurementCommand> measurementCommands) {
        this.measurementCommands = measurementCommands;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Config config = (Config) o;
        return measurementInterval == config.measurementInterval &&
                Double.compare(config.measurementThreshold, measurementThreshold) == 0 &&
                ignoreMeasurements == config.ignoreMeasurements &&
                Objects.equals(vip, config.vip) &&
                Objects.equals(dVipRange, config.dVipRange) &&
                Objects.equals(strategyRanges, config.strategyRanges) &&
                Objects.equals(topology, config.topology) &&
                Objects.equals(loadBalancers, config.loadBalancers) &&
                Objects.equals(weights, config.weights) &&
                Objects.equals(measurementCommands, config.measurementCommands);
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
                ignoreMeasurements,
                weights,
                measurementCommands);
    }
}
