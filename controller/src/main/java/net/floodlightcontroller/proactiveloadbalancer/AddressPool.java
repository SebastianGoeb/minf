package net.floodlightcontroller.proactiveloadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Collections;
import java.util.List;

public class AddressPool {

	@JsonProperty
	@JsonSerialize(contentUsing = ToStringSerializer.class)
	@JsonDeserialize(contentUsing = IPv4AddressDeserializer.class)
	private final List<IPv4Address> dips;

	public AddressPool() {
		this.dips = Collections.emptyList();
	}

	List<IPv4Address> getDips() {
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
