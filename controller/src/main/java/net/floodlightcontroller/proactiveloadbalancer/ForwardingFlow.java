package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

class ForwardingFlow {

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = IPv4AddressWithMaskDeserializer.class)
    private IPv4AddressWithMask prefix;

    @JsonProperty
    private int port;

    ForwardingFlow(IPv4AddressWithMask prefix, int port) {
        this.prefix = prefix;
        this.port = port;
    }

    public IPv4AddressWithMask getPrefix() {
        return prefix;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForwardingFlow that = (ForwardingFlow) o;
        return Objects.equals(prefix, that.prefix) &&
                Objects.equals(port, that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, port);
    }

    @Override
    public String toString() {
        return "Fwd Flow [prefix=" + prefix + ", port=" + port + "]";
    }
}
