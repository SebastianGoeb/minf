package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Topology {

    private Set<Bridge> bridges;
    private Set<Host> hosts;
    private List<ForwardingFlow> forwardingFlows;

    @JsonCreator
    public Topology(
            @JsonProperty("bridges")
                    Set<Bridge> bridges,
            @JsonProperty("hosts")
                    Set<Host> hosts,
            @JsonProperty("forwardingFlows")
                    List<ForwardingFlow> forwardingFlows) {
        Objects.requireNonNull(bridges);
        Objects.requireNonNull(hosts);
        Objects.requireNonNull(forwardingFlows);
        this.bridges = bridges;
        this.hosts = hosts;
        this.forwardingFlows = forwardingFlows;
    }

    public Set<Bridge> getBridges() {
        return bridges;
    }

    public Set<Host> getHosts() {
        return hosts;
    }

    public List<ForwardingFlow> getForwardingFlows() {
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
