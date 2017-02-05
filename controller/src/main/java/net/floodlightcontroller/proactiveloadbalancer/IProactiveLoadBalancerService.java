package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.IPv4Address;

public interface IProactiveLoadBalancerService extends IFloodlightService {
    // Config
    void setVip(IPv4Address vip);
    void setTopology(Topology topology);
    void setStrategy(Strategy strategy);
}
