package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.IPv4Address;

public interface IProactiveLoadBalancerService extends IFloodlightService {
    // Config
    void setStrategy(Strategy strategy);
    void setTopology(Topology topology);
    void setVip(IPv4Address vip);
}
