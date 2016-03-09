package net.floodlightcontroller.serverloadbalancer.web;

import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ServerLoadBalancerStatsResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);


    @Get("json")
    public Map<String, Object> create() throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());


        // Adjust maxPrefixLength
        Map<String, Long> load = slbService.getStats().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().getNwAddress().toString(),
                        e -> e.getValue()
                ));
        Integer rules = slbService.numRules();

        // Construct response
        setStatus(Status.SUCCESS_OK);
        Map<String, Object> response = new HashMap<>();
        response.put("rules", rules);
        response.put("load", load);
        return response;
    }
}
