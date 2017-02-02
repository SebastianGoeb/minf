package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;

public interface ITrafficMeasurementService extends IFloodlightService {
    void setVip(IPv4Address vip);

    void addSwitch(DatapathId dpid);
    DatapathId deleteSwitch(DatapathId dpid);

    void addMeasurementListener(IMeasurementListener listener);
    boolean removeMeasurementListener(IMeasurementListener listener);
}
