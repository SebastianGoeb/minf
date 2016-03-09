package net.floodlightcontroller.serverloadbalancer.web;

import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.serverloadbalancer.Server;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.restlet.data.Status;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerCreateResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);


    @Post("json")
    public List<Server> createServer(String fmJson) throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        // Parse JSON
        List<Server> servers;
        if (fmJson.trim().startsWith("{")) {
            servers = Collections.singletonList(Server.fromJson(fmJson));
        } else if (fmJson.trim().startsWith("[")) {
            servers = Server.fromJsonList(fmJson);
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return null;
        }

        for (Server server : servers) {
            // Validate input
            if (server.getNwAddress() == null) {
                setStatus(Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY, "Missing IP address");
                return null;
            } else if (server.getDlAddress() == null) {
                setStatus(Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY, "Missing MAC address");
                return null;
            } else if (server.getPort() == null) {
                setStatus(Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY, "Missing Port number");
                return null;
            }

            // Add server
            slbService.addServer(server);
        }

        // Adjust maxPrefixLength
        slbService.autoSetMaxPrefixLength();

        // Start transition
        slbService.requestTransition();

        // Construct response
        setStatus(Status.SUCCESS_CREATED);
        return servers;
    }
}
