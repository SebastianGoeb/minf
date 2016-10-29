package net.floodlightcontroller.serverloadbalancer.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.serverloadbalancer.network.LoadBalanceTarget;
import net.floodlightcontroller.serverloadbalancer.network.Switch;
import net.floodlightcontroller.staticflowentry.web.ListStaticFlowEntriesResource;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.data.Status;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SwitchTargetCreateResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);

    @Post("json")
    public List<Link> createSwitchTarget(String fmJson) throws IOException {
        IServerLoadBalancerService slbService =
                (IServerLoadBalancerService) getContext().getAttributes()
                        .get(IServerLoadBalancerService.class.getCanonicalName());
        IOFSwitchService switchManager =
                (IOFSwitchService) getContext().getAttributes()
                        .get(IOFSwitchService.class.getCanonicalName());

        // Add link
        String idString = (String) getRequestAttributes().get("id");
        DatapathId dpid = DatapathId.of(idString.substring(0, 2)
                + ':' + idString.substring(2, 4)
                + ':' + idString.substring(4, 6)
                + ':' + idString.substring(6, 8)
                + ':' + idString.substring(8, 10)
                + ':' + idString.substring(10, 12)
                + ':' + idString.substring(12, 14)
                + ':' + idString.substring(14, 16));
        Switch sw = slbService.getSwitches().stream()
                .filter(s -> s.getDpid().equals(dpid))
                .findFirst()
                .orElse(null);
        if (sw != null) {
            // Parse JSON
            List<Link> links;
            if (fmJson.trim().startsWith("{")) {
                links = Collections.singletonList(Link.fromJson(fmJson));
            } else if (fmJson.trim().startsWith("[")) {
                links = Link.fromJsonList(fmJson);
            } else {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Parse error");
                return null;
            }

            // Validate input
            for (Link link : links) {
                if (link.port == null) {
                    setStatus(Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY, "Missing port");
                    return null;
                } else if (link.targetId < 0) {
                    setStatus(Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY, "Invalid targetId");
                }
            }


            for (Link link : links) {
                LoadBalanceTarget target = slbService.getServers().stream()
                        .filter(server -> server.getId() == link.targetId)
                        .findFirst()
                        .orElse(null);
                slbService.addTarget(sw, link.port, target);
                sw.addTarget(link.port, target);
            }

            // Construct response
            setStatus(Status.SUCCESS_CREATED);
            return links;
        }

        setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Switch dpid does not exist");
        return null;
    }

    public static class Link {
        @JsonProperty("port")
        private String port;
        @JsonProperty("targetId")
        private int targetId;

        public Link() {
            port = null;
            targetId = -1;
        }

        public Link(String port, int targetId) {
            this.port = port;
            this.targetId = targetId;
        }

        public static Link fromJson(String s) throws IOException {
            return new ObjectMapper().readValue(s, Link.class);
        }

        public static List<Link> fromJsonList(String s) throws IOException {
            return new ObjectMapper().readValue(s, new TypeReference<List<Link>>() {
            });
        }
    }
}
