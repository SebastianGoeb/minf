package net.floodlightcontroller.proactiveloadbalancer.web;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class ProactiveLoadBalancerWebRoutable implements RestletRoutable {
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        // Config paths
        router.attach("/strategy", StrategyResource.class);
        router.attach("/topology", TopologyResource.class);
        router.attach("/vip", VipResource.class);
//        router.attach("/traditional_range", SwitchResource.class);
//        router.attach("/bit_shuffle", ShuffleResource.class);
//        router.attach("/overlap", OverlapResource.class);
//        router.attach("/eviction", EvictionResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/proactiveloadbalancer";
    }
}
