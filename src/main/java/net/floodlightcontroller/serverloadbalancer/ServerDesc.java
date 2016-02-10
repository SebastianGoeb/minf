package net.floodlightcontroller.serverloadbalancer;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

public class ServerDesc extends LoadBalanceTarget {
    private IPv4Address nwAddress;
    private MacAddress dlAddress;

    public ServerDesc(IPv4Address nwAddress, MacAddress dlAddress) {
        super();
        this.nwAddress = nwAddress;
        this.dlAddress = dlAddress;
    }

    public IPv4Address getNwAddress() {
        return nwAddress;
    }

    public ServerDesc setNwAddress(IPv4Address nwAddress) {
        this.nwAddress = nwAddress;
        return this;
    }

    public MacAddress getDlAddress() {
        return dlAddress;
    }

    public ServerDesc setDlAddress(MacAddress dlAddress) {
        this.dlAddress = dlAddress;
        return this;
    }
}
