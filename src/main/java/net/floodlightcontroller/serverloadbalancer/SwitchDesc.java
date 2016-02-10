package net.floodlightcontroller.serverloadbalancer;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwitchDesc {
    private IOFSwitch iofSwitch;
    private Map<LoadBalanceTarget, Integer> loadBalanceTargets;

    public SwitchDesc() {
        this(null, new HashMap<>());
    }

    public SwitchDesc(IOFSwitch iofSwitch, Map<LoadBalanceTarget, Integer> loadBalanceTargets) {
        this.iofSwitch = iofSwitch;
        this.loadBalanceTargets = loadBalanceTargets;
    }

    public IOFSwitch getIofSwitch() {
        return iofSwitch;
    }

    public SwitchDesc setIofSwitch(IOFSwitch iofSwitch) {
        this.iofSwitch = iofSwitch;
        return this;
    }

    public Map<LoadBalanceTarget, Integer> getLoadBalanceTargets() {
        return loadBalanceTargets;
    }

    public SwitchDesc setLoadBalanceTargets(Map<LoadBalanceTarget, Integer> loadBalanceTargets) {
        this.loadBalanceTargets = loadBalanceTargets;
        return this;
    }

    public SwitchDesc addLoadBalanceTarget(LoadBalanceTarget loadBalanceTarget, int portNumber) {
        loadBalanceTargets.put(loadBalanceTarget, portNumber);
        return this;
    }

    public SwitchDesc removeLoadBalanceTarget(LoadBalanceTarget loadBalanceTarget) {
        loadBalanceTargets.remove(loadBalanceTarget);
        return this;
    }
}
