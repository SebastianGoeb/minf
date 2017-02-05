package net.floodlightcontroller.proactiveloadbalancer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.proactiveloadbalancer.IProactiveLoadBalancerService;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.data.Status;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;

public class VipResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(VipResource.class);

    @Put("json")
    public Response<?> set(String json) throws IOException {
        IProactiveLoadBalancerService service = ((IProactiveLoadBalancerService) getContext()
            .getAttributes()
            .get(IProactiveLoadBalancerService.class.getCanonicalName()));

        // Parse JSON
        String vipString = new ObjectMapper().readValue(json, String.class);
        IPv4Address vip = null;
        if (vipString != null) {
            try {
                vip = IPv4Address.of(vipString);
            } catch (IllegalArgumentException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return new Response<Void>()
                        .addError(MessageFormat.format("Invalid vip: {0}", vipString));
            }
        }

        // Apply configuration
        service.setVip(vip);

        // Construct response
        setStatus(Status.SUCCESS_CREATED);
        return new Response<String>()
            .setData(vip != null ? vip.toString() : null);
    }
}
