package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IProactiveLoadBalancerService extends IFloodlightService {
    // Config
//    void setVip(IPv4Address vip);
//    void setTopology(Topology topology);
    void setConfig(Config config);
}
