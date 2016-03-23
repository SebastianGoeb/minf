package net.floodlightcontroller.serverloadbalancer.web;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.restlet.data.Status;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AssignmentTreeCreateResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);

    @Post("json")
    public void createServer(String fmJson) throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());

        // Parse JSON
        AssignmentTreeParams params;
        try {
            params = new ObjectMapper().readValue(fmJson, AssignmentTreeParams.class);
        } catch (JsonParseException | JsonMappingException e2) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid JSON");
            return;
        }

        // Adjust maxPrefixLength
        if (params.maxPrefixLength.equals("auto")) {
            slbService.setMaxPrefixLength();
        } else {
            slbService.setMaxPrefixLength(Integer.parseInt(params.maxPrefixLength));
        }

        // Start transition
        slbService.requestTransition(params.fromCurrent);

        // Construct response
        setStatus(Status.SUCCESS_CREATED);
    }

    public static class AssignmentTreeParams {
        public boolean fromCurrent;
        public String maxPrefixLength;
    }
}
