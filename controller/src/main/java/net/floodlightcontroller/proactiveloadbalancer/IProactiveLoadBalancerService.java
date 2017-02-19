package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.proactiveloadbalancer.domain.Config;

public interface IProactiveLoadBalancerService extends IFloodlightService {
    void setConfig(Config config);
}
