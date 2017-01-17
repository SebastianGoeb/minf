package net.floodlightcontroller.proactiveloadbalancer.web;

import java.io.IOException;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import net.floodlightcontroller.proactiveloadbalancer.AddressPool;
import net.floodlightcontroller.proactiveloadbalancer.IProactiveLoadBalancerService;

public class AddressPoolResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(AddressPoolResource.class);

	@Put("json")
	public String createOrUpdateAddressPool(String fmJson) throws IOException {
		IProactiveLoadBalancerService lbService = (IProactiveLoadBalancerService) getContext().getAttributes()
				.get(IProactiveLoadBalancerService.class.getCanonicalName());
		
		// Validate VIP
		IPv4Address vip;
		try {
			vip = IPv4Address.of((String) getRequestAttributes().get("vip"));
		} catch (IllegalArgumentException e) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return "{\"error\":\"Invalid VIP\"}";
		}
		
		// Parse JSON
		ObjectMapper mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addDeserializer(IPv4Address.class, new IPv4AddressJsonDeserializer(IPv4Address.class));
		module.addSerializer(IPv4Address.class, new ToStringSerializer());
		mapper.registerModule(module);
		AddressPool addressPool = mapper.readValue(fmJson, AddressPool.class);

		// Add DIP address pool for VIP
		lbService.addAddressPool(vip, addressPool);

		// Construct response
		setStatus(Status.SUCCESS_CREATED);
		
		return mapper.writeValueAsString(addressPool);
	}

	@Delete("json")
	public String deleteAddressPool() throws IOException {
		IProactiveLoadBalancerService lbService = (IProactiveLoadBalancerService) getContext().getAttributes()
				.get(IProactiveLoadBalancerService.class.getCanonicalName());
		
		// Validate VIP
		IPv4Address vip;
		try {
			vip = IPv4Address.of((String) getRequestAttributes().get("vip"));
		} catch (IllegalArgumentException e) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return "{\"error\":\"Invalid VIP\"}";
		}

		// Delete DIP address pool for VIP
		AddressPool deletedAddressPool = lbService.deleteAddressPool(vip);
		if (deletedAddressPool != null) {
			setStatus(Status.SUCCESS_OK);
			return "\"success\"";
		} else {
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return "{\"error\":\"No address pool for VIP " + vip + "\"}";
		}
	}
}
