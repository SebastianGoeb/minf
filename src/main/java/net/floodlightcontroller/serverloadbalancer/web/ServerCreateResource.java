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

public class ServerCreateResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);


    @Post("json")
    public Server create(String fmJson) throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        // Parse JSON
        Server server = Server.fromJson(fmJson);

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
        slbService.requestTransition();

        // Construct response
        setStatus(Status.SUCCESS_CREATED);
        return server;
    }
}
