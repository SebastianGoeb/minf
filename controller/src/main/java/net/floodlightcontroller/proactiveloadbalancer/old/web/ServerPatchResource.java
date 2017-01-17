package net.floodlightcontroller.proactiveloadbalancer.old.web;

import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerPatchResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ServerPatchResource.class);

//
//    @Patch("json")
//    public String create(String fmJson) {
//        log.info(fmJson);
//        log.info((String) getRequestAttributes().get("id"));
//        IServerLoadBalancerService slbService = (IServerLoadBalancerService) getContext().getAttributes().get(IServerLoadBalancerService.class.getCanonicalName());
//        slbService.addServer(null);
//        String param = (String) getRequestAttributes().get("id");
//        try {
//            Server server = Server.fromJson(fmJson);
//            log.info(server.toString());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return "\"I can create servers\"\n";
//    }
}
