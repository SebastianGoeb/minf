package net.floodlightcontroller.serverloadbalancer.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.serverloadbalancer.network.Server;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.restlet.data.Status;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
            }

            // Add server
            slbService.addServer(server);
        }

        // Construct response
        setStatus(Status.SUCCESS_CREATED);
        return servers;
    }

    @Put("json")
    public void updateSwitch(String fmJson) throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        List<Server> servers = new ArrayList<>(slbService.getServers());

        Map<String, Double> map = new ObjectMapper().readValue(fmJson, new TypeReference<Map<String, Double>>() {});
        for (Server server : servers) {
            String ipString = server.getNwAddress().toString();
            if (map.containsKey(ipString)) {
                server.setWeight(map.get(ipString));
            }
        }
        setStatus(Status.SUCCESS_CREATED);
        return;
    }
}
