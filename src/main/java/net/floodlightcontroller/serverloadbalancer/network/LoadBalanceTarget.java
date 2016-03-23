package net.floodlightcontroller.serverloadbalancer.network;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class LoadBalanceTarget implements ForwardingTarget {
    private static int nextId = 0;

    @JsonProperty("id")
    protected int id;

    protected LoadBalanceTarget() {
        this.id = nextId++;
    }

    public int getId() {
        return id;
    }

    public abstract double getWeight();
}
