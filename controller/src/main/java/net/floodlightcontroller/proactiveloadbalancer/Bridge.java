package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Collections;
import java.util.List;

class Bridge {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = DatapathIdDeserializer.class)
    private DatapathId dpid;

    @JsonProperty
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @JsonDeserialize(contentUsing = DatapathIdDeserializer.class)
    private List<DatapathId> upstreamBridges;

    @JsonProperty
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @JsonDeserialize(contentUsing = IPv4AddressDeserializer.class)
    private List<IPv4Address> upstreamHosts;

    public Bridge() {
        upstreamBridges = Collections.emptyList();
        upstreamHosts = Collections.emptyList();
    }

    public DatapathId getDpid() {
        return dpid;
    }

    public List<DatapathId> getUpstreamBridges() {
        return upstreamBridges;
    }

    public List<IPv4Address> getUpstreamHosts() {
        return upstreamHosts;
    }
}
