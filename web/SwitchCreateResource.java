package net.floodlightcontroller.serverloadbalancer.web;

import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.serverloadbalancer.network.Switch;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.restlet.data.Status;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SwitchCreateResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);

    @Post("json")
    public List<Switch> createSwitch(String fmJson) throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        // Parse JSON
        List<Switch> switches;
        if (fmJson.trim().startsWith("{")) {
            switches = Collections.singletonList(Switch.fromJson(fmJson));
        } else if (fmJson.trim().startsWith("[")) {
            switches = Switch.fromJsonList(fmJson);
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Parse error");
            return null;
        }

        // Validate input
        for (Switch sw : switches) {
            if (sw.getDpid() == null) {
                setStatus(Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY, "Missing IP address");
                return null;
            }

            // Add switch
            slbService.addSwitch(sw);
        }

        // Construct response
        setStatus(Status.SUCCESS_CREATED);
        return switches;
    }
}
