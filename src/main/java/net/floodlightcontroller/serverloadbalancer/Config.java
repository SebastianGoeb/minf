package net.floodlightcontroller.serverloadbalancer;

import java.util.ArrayList;
import java.util.List;

public class Config {
    // Assignments
    private int maxPrefixLength;
    private List<Double> weights;
    private SwitchDesc coreSwitch;
    private List<Server> servers;

    // Stats
    private long loadStatsInterval;

    public Config() {
        maxPrefixLength = 3;
        weights = new ArrayList<>();
        servers = new ArrayList<>();
    }

    // Max Prefix Length
    public int getMaxPrefixLength() {
        return maxPrefixLength;
    }

    public Config setMaxPrefixLength(int maxPrefixLength) {
        this.maxPrefixLength = maxPrefixLength;
        return this;
    }

    // Weights
    public List<Double> getWeights() {
        return weights;
    }

    public Config setWeights(List<Double> weights) {
        this.weights = weights;
        return this;
    }

    // Load Stats Interval
    public long getLoadStatsInterval() {
        return loadStatsInterval;
    }

    public Config setLoadStatsInterval(long loadStatsInterval) {
        this.loadStatsInterval = loadStatsInterval;
        return this;
    }

    // Core Switch
    public SwitchDesc getCoreSwitch() {
        return coreSwitch;
    }

    public Config setCoreSwitch(SwitchDesc coreSwitch) {
        this.coreSwitch = coreSwitch;
        return this;
    }

    // Servers
    public List<Server> getServers() {
        return servers;
    }

    public Config setServers(List<Server> servers) {
        this.servers = servers;
        return this;
    }

    public Config addServer(Server server, int portNumber) {
        servers.add(server);
        coreSwitch.addLoadBalanceTarget(server, portNumber);
        return this;
    }

    public Config removeServer(Server server) {
        servers.remove(server);
        coreSwitch.removeLoadBalanceTarget(server);
        return this;
    }
}
