package net.floodlightcontroller.proactiveloadbalancer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.floodlightcontroller.proactiveloadbalancer.serializer.DatapathIdDeserializer;
import net.floodlightcontroller.proactiveloadbalancer.serializer.DatapathIdKeyDeserializer;
import net.floodlightcontroller.proactiveloadbalancer.serializer.IPv4AddressDeserializer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

public class Topology {

    @JsonProperty
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @JsonDeserialize(contentUsing = IPv4AddressDeserializer.class)
    private List<IPv4Address> servers;

    @JsonProperty
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @JsonDeserialize(contentUsing = DatapathIdDeserializer.class)
    private List<DatapathId> switches;

    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    private Map<DatapathId, Map<IPv4Address, Integer>> downlinksToServers;

    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    @JsonDeserialize(keyUsing = DatapathIdKeyDeserializer.class)
    private Map<DatapathId, Map<DatapathId, Integer>> downlinksToSwitches;

    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    @JsonDeserialize(keyUsing = DatapathIdKeyDeserializer.class)
    private Map<DatapathId, Map<DatapathId, Integer>> uplinksToSwitches;

    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    private Map<DatapathId, Map<IPv4AddressWithMask, Integer>> uplinksToClients;

    public List<IPv4Address> getServers() {
        return servers;
    }

    public List<DatapathId> getSwitches() {
        return switches;
    }

    public Map<DatapathId, Map<IPv4Address, Integer>> getDownlinksToServers() {
        return downlinksToServers;
    }

    public Map<DatapathId, Map<DatapathId, Integer>> getDownlinksToSwitches() {
        return downlinksToSwitches;
    }

    public Map<DatapathId, Map<DatapathId, Integer>> getUplinksToSwitches() {
        return uplinksToSwitches;
    }

    public Map<DatapathId, Map<IPv4AddressWithMask, Integer>> getUplinksToClients() {
        return uplinksToClients;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Topology topology = (Topology) o;
        return Objects.equals(servers, topology.servers) &&
                Objects.equals(switches, topology.switches) &&
                Objects.equals(downlinksToServers, topology.downlinksToServers) &&
                Objects.equals(downlinksToSwitches, topology.downlinksToSwitches);
    }

    @Override
    public int hashCode() {
        return Objects.hash(servers, switches, downlinksToServers, downlinksToSwitches);
    }

    public boolean isCoreSwitch(DatapathId dpid) {
        return getUplinksToClients().containsKey(dpid);
    }

    public List<DatapathId> getAccessSwitches() {
        return getSwitches().stream()
                .filter(dpid -> getDownlinksToServers().containsKey(dpid))
                .filter(dpid -> !getDownlinksToServers().get(dpid).isEmpty())
                .collect(toList());
    }
}
