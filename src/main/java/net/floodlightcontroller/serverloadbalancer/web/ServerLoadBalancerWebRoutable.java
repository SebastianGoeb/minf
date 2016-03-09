package net.floodlightcontroller.serverloadbalancer.web;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class ServerLoadBalancerWebRoutable implements RestletRoutable{
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/servers", ServerCreateResource.class);
//        router.attach("/servers/{id}", ServerPatchResource.class);
        router.attach("/servers/{id}", ServerDeleteResource.class);
        router.attach("/stats", ServerLoadBalancerStatsResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/serverloadbalancer";
    }
}
