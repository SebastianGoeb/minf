package net.floodlightcontroller.proactiveloadbalancer.web;

import net.floodlightcontroller.proactiveloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.proactiveloadbalancer.network.Server;
import org.restlet.resource.Delete;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServerDeleteResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ServerDeleteResource.class);


    @Delete("json")
    public void deleteServer() throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        String idString = (String) getRequestAttributes().get("id");
        List<Server> servers = new ArrayList<>(slbService.getServers());

        if (idString.equals("all")) {
            servers.forEach(slbService::removeServer);
        } else if (idString.matches("^\\d+")) {
            int id = Integer.parseInt(idString);
            for (Server server : servers) {
                if (server.getId() == id) {
                    slbService.removeServer(server);
                }
            }
        } else if (idString.matches("^\\d+(,\\d+)*")) {
            List<Integer> ids = new ArrayList<>();
            for (String s : idString.split(",")) {
                ids.add(Integer.parseInt(s));
            }

            for (Server server : servers) {
                if (ids.contains(server.getId())) {
                    slbService.removeServer(server);
                }
            }
        }
    }
}
