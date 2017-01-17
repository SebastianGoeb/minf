package net.floodlightcontroller.proactiveloadbalancer.web;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class ProactiveLoadBalancerWebRoutable implements RestletRoutable{
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/addresspools/{vip}", AddressPoolResource.class);
        router.attach("/switches/{dpid}", SwitchResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/proactiveloadbalancer";
    }
}
