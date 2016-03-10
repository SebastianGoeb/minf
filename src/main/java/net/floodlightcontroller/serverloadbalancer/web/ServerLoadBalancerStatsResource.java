package net.floodlightcontroller.serverloadbalancer.web;

import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.serverloadbalancer.Server;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.projectfloodlight.openflow.types.DatapathId;
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


        // TODO this won't make sense in a hierarchical setting because throughput from switches in series don't add
        Map<String, Long> overallLoadMap = new HashMap<>();
        for (DatapathId dpid : slbService.getDpids()) {
            for (Map.Entry<Server, Long> entry : slbService.getStats(dpid).entrySet()) {
                String ipString = entry.getKey().getNwAddress().toString();
                long load = entry.getValue();
                if (!overallLoadMap.containsKey(ipString)) {
                    overallLoadMap.put(ipString, 0L);
                }
                overallLoadMap.put(ipString, overallLoadMap.get(ipString) + load);
            }
        }


        Map<DatapathId, Integer> rulesMap = slbService.getDpids().stream()
                .collect(Collectors.toMap(
                        dpid -> dpid,
                        slbService::numRules
                ));

        // Construct response
        setStatus(Status.SUCCESS_OK);
        Map<String, Object> response = new HashMap<>();
        response.put("rules", rulesMap);
        response.put("load", overallLoadMap);
        return response;
    }
}
