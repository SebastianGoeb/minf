package net.floodlightcontroller.proactiveloadbalancer;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IProactiveLoadBalancerService extends IFloodlightService {
    // Config
    void setStrategy(Strategy strategy);
    void setTopology(Topology topology);
    void setVip(IPv4Address vip);

    @Deprecated
	void addSwitch(DatapathId dpid);
    @Deprecated
	DatapathId deleteSwitch(DatapathId dpid);

    @Deprecated
    void addAddressPool(IPv4Address vip, AddressPool addressPool);
    @Deprecated
    AddressPool deleteAddressPool(IPv4Address vip);
}
