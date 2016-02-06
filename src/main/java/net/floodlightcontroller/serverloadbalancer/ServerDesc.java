package net.floodlightcontroller.serverloadbalancer;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

public class ServerDesc {
    public int portNumber;
    public IPv4Address nwAddress;
    public MacAddress dlAddress;

    public ServerDesc(int portNumber, IPv4Address nwAddress, MacAddress dlAddress) {
        this.portNumber = portNumber;
        this.nwAddress = nwAddress;
        this.dlAddress = dlAddress;
    }
}
