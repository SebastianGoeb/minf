package net.floodlightcontroller.proactiveloadbalancer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.proactiveloadbalancer.IProactiveLoadBalancerService;
import net.floodlightcontroller.proactiveloadbalancer.Topology;
import org.restlet.data.Status;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TopologyResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(TopologyResource.class);

    @Put("json")
    public Response<?> set(String json) throws IOException {
        IProactiveLoadBalancerService service = ((IProactiveLoadBalancerService) getContext()
            .getAttributes()
            .get(IProactiveLoadBalancerService.class.getCanonicalName()));

        // Parse JSON
        Topology topology;
        try {
            topology = new ObjectMapper().readValue(json, Topology.class);
        } catch (IOException e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return new Response<Void>()
                    .addError("Invalid json");
        }

        // Apply configuration
        service.setTopology(topology);

        // Construct response
        setStatus(Status.SUCCESS_CREATED);
        return new Response<Topology>()
            .setData(topology);
    }
}
