package net.floodlightcontroller.proactiveloadbalancer;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

public class Rule {
	private final IPv4AddressWithMask prefix;
	private final IPv4Address dip;
	
	public Rule(IPv4AddressWithMask prefix, IPv4Address dip) {
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dip == null) ? 0 : dip.hashCode());
		result = prime * result + ((prefix == null) ? 0 : prefix.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Rule)) {
			return false;
		}
		Rule other = (Rule) obj;
		if (dip == null) {
			if (other.dip != null) {
				return false;
			}
		} else if (!dip.equals(other.dip)) {
			return false;
		}
		if (prefix == null) {
			if (other.prefix != null) {
				return false;
			}
		} else if (!prefix.equals(other.prefix)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "Rule [prefix=" + prefix + ", dip=" + dip + "]";
	}
}
