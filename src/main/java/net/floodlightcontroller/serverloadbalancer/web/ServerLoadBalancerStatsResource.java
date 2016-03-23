package net.floodlightcontroller.serverloadbalancer.web;

import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.serverloadbalancer.ServerLoadBalancer.Stats;
import net.floodlightcontroller.serverloadbalancer.network.LoadBalanceTarget;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class ServerLoadBalancerStatsResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);


    @Get("json")
    public Map<Integer, Stats> create() throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        Map<Integer, Stats> stats = slbService.getSwitches().stream()
                .collect(Collectors.toMap(
                        LoadBalanceTarget::getId,
                        slbService::getStats
                ));

        // Construct response
        setStatus(Status.SUCCESS_OK);
        return stats;
    }
}
