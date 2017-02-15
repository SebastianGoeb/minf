package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

class LoadBalancingFlow {


	@JsonProperty
	@JsonSerialize(using = ToStringSerializer.class)
	@JsonDeserialize(using = IPv4AddressWithMaskDeserializer.class)
	private final IPv4AddressWithMask prefix;

	@JsonProperty
	@JsonSerialize(using = ToStringSerializer.class)
	@JsonDeserialize(using = IPv4AddressDeserializer.class)
	private final IPv4Address dip;
	
	LoadBalancingFlow(IPv4AddressWithMask prefix, IPv4Address dip) {
		this.prefix = prefix;
		this.dip = dip;
	}
	
	public IPv4AddressWithMask getPrefix() {
		return prefix;
	}

	public IPv4Address getDip() {
		return dip;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LoadBalancingFlow that = (LoadBalancingFlow) o;
		return Objects.equals(prefix, that.prefix) &&
				Objects.equals(dip, that.dip);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prefix, dip);
	}

	@Override
	public String toString() {
		return "LB Flow [prefix=" + prefix + ", dip=" + dip + "]";
	}
}
