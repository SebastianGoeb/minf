package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

class Bridge {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = DatapathIdDeserializer.class)
    private DatapathId dpid;

    @JsonProperty
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @JsonDeserialize(contentUsing = DatapathIdDeserializer.class)
    private Set<DatapathId> upstreamBridges;

    @JsonProperty
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @JsonDeserialize(contentUsing = IPv4AddressDeserializer.class)
    private Set<IPv4Address> upstreamHosts;

    Bridge() {
        dpid = DatapathId.NONE;
        upstreamBridges = Collections.emptySet();
        upstreamHosts = Collections.emptySet();
    }

    DatapathId getDpid() {
        return dpid;
    }

    Set<DatapathId> getUpstreamBridges() {
        return upstreamBridges;
    }

    Set<IPv4Address> getUpstreamHosts() {
        return upstreamHosts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bridge bridge = (Bridge) o;
        return Objects.equals(dpid, bridge.dpid) &&
                Objects.equals(upstreamBridges, bridge.upstreamBridges) &&
                Objects.equals(upstreamHosts, bridge.upstreamHosts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dpid, upstreamBridges, upstreamHosts);
    }
}
