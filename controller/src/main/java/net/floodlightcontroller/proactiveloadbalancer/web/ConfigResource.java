package net.floodlightcontroller.proactiveloadbalancer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.proactiveloadbalancer.Config;
import net.floodlightcontroller.proactiveloadbalancer.IProactiveLoadBalancerService;
import org.restlet.data.Status;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ConfigResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ConfigResource.class);

    @Put("json")
    public Response<?> set(String json) throws IOException {
        IProactiveLoadBalancerService service = ((IProactiveLoadBalancerService) getContext()
                .getAttributes()
                .get(IProactiveLoadBalancerService.class.getCanonicalName()));

        // Parse JSON
        Config config;
        try {
            config = new ObjectMapper().readValue(json, Config.class);
        } catch (IOException e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return new Response<Void>()
                    .addError("Invalid json");
        }

        // Apply configuration
        service.setConfig(config);

        // Construct response
        setStatus(Status.SUCCESS_OK);
        return new Response<Config>()
                .setData(config);
    }
}
