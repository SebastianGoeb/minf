package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Topology {

    @JsonProperty
    private Set<Bridge> bridges;

    @JsonProperty
    private Set<Host> hosts;

    @JsonProperty
    private List<ForwardingFlow> forwardingFlows;

    Topology() {
        bridges = Collections.emptySet();
        hosts = Collections.emptySet();
        forwardingFlows = Collections.emptyList();
    }

    Set<Bridge> getBridges() {
        return bridges;
    }

    Set<Host> getHosts() {
        return hosts;
    }

    List<ForwardingFlow> getForwardingFlows() {
        return forwardingFlows;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Topology topology = (Topology) o;
        return Objects.equals(bridges, topology.bridges) &&
                Objects.equals(hosts, topology.hosts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bridges, hosts);
    }
}
