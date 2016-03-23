package net.floodlightcontroller.serverloadbalancer.network;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.serverloadbalancer.IServerLoadBalancerService;
import net.floodlightcontroller.serverloadbalancer.web.SwitchTargetCreateResource.Link;
import org.projectfloodlight.openflow.types.DatapathId;
import org.python.google.common.collect.BiMap;
import org.python.google.common.collect.HashBiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Switch extends LoadBalanceTarget {
    private static Logger log = LoggerFactory.getLogger(Switch.class);
    private IServerLoadBalancerService slbService;

    private DatapathId dpid;

    @JsonIgnore
    private BiMap<String, LoadBalanceTarget> targets;

    public Switch() {
        this(DatapathId.NONE);
    }

    public Switch(DatapathId dpid) {
        super();
        this.dpid = dpid;
        this.targets = HashBiMap.create();
    }

    public static Switch fromJson(String fmJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(fmJson, Switch.class);
    }

    public static List<Switch> fromJsonList(String fmJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(fmJson, new TypeReference<List<Switch>>() {
        });
    }

    public DatapathId getDpid() {
        return dpid;
    }

    public BiMap<String, LoadBalanceTarget> getTargets() {
        return targets;
    }

    public Switch setSlbService(IServerLoadBalancerService slbService) {
        this.slbService = slbService;
        return this;
    }

    public void addTarget(String port, LoadBalanceTarget target) {
        targets.put(port, target);
    }

    public void removeTarget(LoadBalanceTarget target) {
        targets.inverse().remove(target);
    }

    public LoadBalanceTarget getTarget(String port) {
        return targets.get(port);
    }

    public String getPort(LoadBalanceTarget target) {
        return targets.inverse().get(target);
    }

    @JsonGetter("dpid")
    public String getJsonDpid() {
        return dpid.toString().replace(":", "");
    }

    @JsonSetter("dpid")
    public Switch setJsonDpid(String s) {
        String sb = s.substring(0, 2)
                + ':' + s.substring(2, 4)
                + ':' + s.substring(4, 6)
                + ':' + s.substring(6, 8)
                + ':' + s.substring(8, 10)
                + ':' + s.substring(10, 12)
                + ':' + s.substring(12, 14)
                + ':' + s.substring(14, 16);
        dpid = DatapathId.of(sb);
        return this;
    }

    @JsonGetter("targets")
    public List<Link> getJsonTargets() {
        return targets.entrySet().stream()
                .map(e -> new Link(
                        e.getKey(),
                        e.getValue().getId()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public double getWeight() {
        double sum = 0;
        for (LoadBalanceTarget target : targets.values()) {
            sum += target.getWeight();
        }
        return sum;
    }

    @Override
    public boolean equals(Object obj) {
        return (this == obj)
                || (obj instanceof Switch
                && Objects.equals(dpid, ((Switch) obj).dpid
        ));
    }

    @Override
    public String toString() {
        return "Switch " + dpid;
    }
}
