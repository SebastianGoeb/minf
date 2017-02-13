package net.floodlightcontroller.proactiveloadbalancer.web;

import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class StrategyResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StrategyResource.class);

    @Put("json")
    public Response<?> set(String json) throws IOException {
//        IProactiveLoadBalancerService service = ((IProactiveLoadBalancerService) getContext()
//                .getAttributes()
//                .get(IProactiveLoadBalancerService.class.getCanonicalName()));
//
//        // Parse JSON
//        String strategyString = new ObjectMapper().readValue(json, String.class);
//        Strategy strategy = null;
//        if (strategyString != null) {
//            try {
//                strategy = Strategy.valueOf(strategyString);
//            } catch (IllegalArgumentException e) {
//                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
//                return new Response<Void>()
//                        .addError(MessageFormat.format("Invalid strategy: {0}", strategyString));
//            }
//        }
//
//        // Apply configuration
//        service.setStrategy(strategy);
//
//        // Construct response
//        setStatus(Status.SUCCESS_CREATED);
//        return new Response<StrategyRange>()
//                .setData(strategy);
        return null;
    }
}
