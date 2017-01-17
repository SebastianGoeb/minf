package net.floodlightcontroller.proactiveloadbalancer.web;

import java.io.IOException;

import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.proactiveloadbalancer.IProactiveLoadBalancerService;

public class SwitchResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(SwitchResource.class);

	@Put("json")
	public String createOrUpdateSwitch(String fmJson) throws IOException {
		IProactiveLoadBalancerService lbService = (IProactiveLoadBalancerService) getContext().getAttributes()
				.get(IProactiveLoadBalancerService.class.getCanonicalName());
		
		// Validate DPID
		DatapathId dpid;
		try {
			dpid = DatapathId.of((String) getRequestAttributes().get("dpid"));
		} catch (IllegalArgumentException e) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return "{\"error\":\"Invalid DPID\"}";
		}

		// Add switch
		lbService.addSwitch(dpid);

		// Construct response
		setStatus(Status.SUCCESS_CREATED);
		return "\"success\"";
	}

	@Delete("json")
	public String deleteSwitch() throws IOException {
		IProactiveLoadBalancerService lbService = (IProactiveLoadBalancerService) getContext().getAttributes()
				.get(IProactiveLoadBalancerService.class.getCanonicalName());
		
		// Validate DPID
		DatapathId dpid;
		try {
			dpid = DatapathId.of((String) getRequestAttributes().get("dpid"));
		} catch (IllegalArgumentException e) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return "{\"error\":\"Invalid VIP\"}";
		}

		// Delete VIP -> DIP mapping
		DatapathId deletedDpid = lbService.deleteSwitch(dpid);
		if (deletedDpid != null) {
			setStatus(Status.SUCCESS_OK);
			return "\"success\"";
		} else {
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return "{\"error\":\"No switch for DPID " + dpid + "\"}";
		}
	}
}
