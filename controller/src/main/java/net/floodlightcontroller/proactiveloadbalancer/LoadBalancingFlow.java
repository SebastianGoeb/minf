package net.floodlightcontroller.proactiveloadbalancer;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Objects;

class LoadBalancingFlow {
	private final IPv4AddressWithMask prefix;
	private final IPv4Address dip;
	
	LoadBalancingFlow(IPv4AddressWithMask prefix, IPv4Address dip) {
		this.prefix = prefix;
		this.dip = dip;
	}
	
	IPv4AddressWithMask getPrefix() {
		return prefix;
	}

	IPv4Address getDip() {
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
		return "LbFlow [prefix=" + prefix + ", dip=" + dip + "]";
	}
}
