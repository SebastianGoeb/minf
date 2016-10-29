package net.floodlightcontroller.serverloadbalancer.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.serverloadbalancer.network.Server;
import net.floodlightcontroller.serverloadbalancer.network.Switch;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.restlet.resource.Delete;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SwitchDeleteResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);

    @Delete("json")
    public void deleteSwitch() throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        String idString = (String) getRequestAttributes().get("id");
        List<Switch> switches = new ArrayList<>(slbService.getSwitches());

        if (idString.equals("all")) {
            switches.forEach(slbService::removeSwitch);
        } else if (idString.matches("^\\d+")) {
            int id = Integer.parseInt(idString);
            for (Switch sw : switches) {
                if (sw.getId() == id) {
                    slbService.removeSwitch(sw);
                }
            }
        } else if (idString.matches("^\\d+(,\\d+)*")) {
            List<Integer> ids = new ArrayList<>();
            for (String s : idString.split(",")) {
                ids.add(Integer.parseInt(s));
            }

            for (Switch sw : switches) {
                if (ids.contains(sw.getId())) {
                    slbService.removeSwitch(sw);
                }
            }
        }
    }
}
