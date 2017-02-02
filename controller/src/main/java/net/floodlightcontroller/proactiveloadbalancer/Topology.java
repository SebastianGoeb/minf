package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Topology {

    @JsonProperty
    private List<Bridge> bridges;

    @JsonProperty
    private List<Host> hosts;

    public List<Bridge> getBridges() {
        return bridges;
    }

    public List<Host> getHosts() {
        return hosts;
    }
}
