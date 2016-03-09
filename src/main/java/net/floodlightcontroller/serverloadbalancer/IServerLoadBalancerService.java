package net.floodlightcontroller.serverloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.List;
import java.util.Map;

public interface IServerLoadBalancerService extends IFloodlightService {
    public void requestTransition();

    public int numRules();

    // Servers
    public void addServer(Server server);
    public Server getServer(int id);
    public void removeServers(List<Integer> ids);
    public void removeAllServers();

    //Stats
    public Map<Server, Long> getStats();

    public void autoSetMaxPrefixLength();
}
