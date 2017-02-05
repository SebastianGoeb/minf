package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class Topology {

    @JsonProperty
    private Set<Bridge> bridges;

    @JsonProperty
    private Set<Host> hosts;

    Topology() {
        bridges = Collections.emptySet();
        hosts = Collections.emptySet();
    }

    Set<Bridge> getBridges() {
        return bridges;
    }

    Set<Host> getHosts() {
        return hosts;
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
