package net.floodlightcontroller.proactiveloadbalancer;

import java.util.Iterator;
import java.util.List;

import org.projectfloodlight.openflow.types.IPv4Address;

import com.google.common.collect.Ordering;

public class AddressPool {
	private List<IPv4Address> dips;

	public List<IPv4Address> getDips() {
		return dips;
	}

	public void setDips(List<IPv4Address> dips) {
		this.dips = dips;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof AddressPool)) {
			return false;
		}
		AddressPool ap = (AddressPool) obj;
		if (dips.size() != ap.dips.size()) {
			return false;
		}

		// This would be easier with sets (set.equals(otherSet))
		List<IPv4Address> sortedDips = Ordering.natural().sortedCopy(dips);
		List<IPv4Address> otherSortedDips = Ordering.natural().sortedCopy(ap.dips);
		Iterator<IPv4Address> it1 = sortedDips.iterator();
		Iterator<IPv4Address> it2 = otherSortedDips.iterator();
		while(it1.hasNext() && it2.hasNext()) {
		   IPv4Address a1 = it1.next();
		   IPv4Address a2 = it2.next();
		   if (!a1.equals(a2)) {
			   return false;
		   }
		}
		
		return true;
	}
}
