package net.floodlightcontroller.serverloadbalancer.web;

import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.restlet.resource.Delete;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ServerDeleteResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);


    @Delete("json")
    public void deleteServer() throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        String idString = (String) getRequestAttributes().get("id");

        if (idString.equals("all")) {
            slbService.removeAllServers();
        } else if (idString.matches("^\\d+")) {
            List<Integer> ids = Collections.singletonList(Integer.parseInt(idString));
            slbService.removeServers(ids);
        } else if (idString.matches("^\\d+(,\\d+)*")) {
            List<Integer> ids = Arrays.stream(idString.split(","))
                    .map(s -> Integer.parseInt(s))
                    .distinct()
                    .collect(Collectors.toList());
            slbService.removeServers(ids);
        }

        // Adjust maxPrefixLength
        slbService.autoSetMaxPrefixLength();

        // Start transition
        slbService.requestTransition();
    }
}
