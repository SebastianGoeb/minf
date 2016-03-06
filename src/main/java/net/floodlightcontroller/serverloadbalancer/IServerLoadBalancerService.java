package net.floodlightcontroller.serverloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IServerLoadBalancerService extends IFloodlightService {
    public void requestTransition();

    // Servers
    public void addServer(Server server);
    public Server getServer(int id);
    public boolean removeServer(int id);

    //Stats
    public void getStats();
}
