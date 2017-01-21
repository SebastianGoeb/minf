package net.floodlightcontroller.proactiveloadbalancer;

import java.util.Collections;
import java.util.List;

import org.projectfloodlight.openflow.types.IPv4Address;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddressPool {
	private final List<IPv4Address> dips;

	@JsonCreator
	public AddressPool(@JsonProperty("dips") List<IPv4Address> dips) {
		this.dips = Collections.unmodifiableList(dips);
	}

	public List<IPv4Address> getDips() {
		return dips;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dips == null) ? 0 : dips.hashCode());
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
		if (!(obj instanceof AddressPool)) {
			return false;
		}
		AddressPool other = (AddressPool) obj;
		if (dips == null) {
			if (other.dips != null) {
				return false;
			}
		} else if (!dips.equals(other.dips)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "AddressPool [dips=" + dips + "]";
	}
}
