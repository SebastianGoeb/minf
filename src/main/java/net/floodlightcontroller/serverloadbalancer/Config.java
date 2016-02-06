package net.floodlightcontroller.serverloadbalancer;

import java.util.ArrayList;
import java.util.List;

public class Config {
    private int maxPrefixLength;
    private List<Double> weights;
    private List<ServerDesc> servers;

    public Config() {
        maxPrefixLength = 3;
        weights = new ArrayList<>();
        servers = new ArrayList<>();
    }

    public int getMaxPrefixLength() {
        return maxPrefixLength;
    }

    public Config setMaxPrefixLength(int maxPrefixLength) {
        this.maxPrefixLength = maxPrefixLength;
        return this;
    }

    public List<Double> getWeights() {
        return weights;
    }

    public Config setWeights(List<Double> weights) {
        this.weights = weights;
        return this;
    }

    public List<ServerDesc> getServers() {
        return servers;
    }

    public Config setServers(List<ServerDesc> servers) {
        this.servers = servers;
        return this;
    }
}
