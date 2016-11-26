package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.proactiveloadbalancer.ServerLoadBalancer.Stats;
import net.floodlightcontroller.proactiveloadbalancer.network.LoadBalanceTarget;
import net.floodlightcontroller.proactiveloadbalancer.network.Server;
import net.floodlightcontroller.proactiveloadbalancer.network.Switch;

import java.util.List;

public interface IServerLoadBalancerService extends IFloodlightService {
    void setMaxPrefixLength();
    void setMaxPrefixLength(int maxPrefixLength);

    // Servers
    void addServer(Server server);
    List<Server> getServers();
    void removeServer(Server server);

    // Switches
    void addSwitch(Switch sw);
    List<Switch> getSwitches();
    void removeSwitch(Switch sw);

    // Targets
    void addTarget(Switch sw, String portName, LoadBalanceTarget target);

    // Transitions
    void requestTransition(boolean fromCurrent);

    // Stats methods
    Stats getStats(Switch sw);
}
