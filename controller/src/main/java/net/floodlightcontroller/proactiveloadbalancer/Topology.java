package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
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
}
