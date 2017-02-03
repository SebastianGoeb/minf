package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Set;

public interface ITrafficMeasurementService extends IFloodlightService {
    // Config
    void setDpids(Set<DatapathId> dpids);

    // Listeners
    void addMeasurementListener(IMeasurementListener listener);

    // Measurements
    PrefixTrie<Long> getMeasurement(DatapathId dpid);
}
