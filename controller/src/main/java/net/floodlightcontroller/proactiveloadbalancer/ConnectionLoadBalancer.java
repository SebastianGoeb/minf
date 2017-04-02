package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.collect.Iterators;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.proactiveloadbalancer.domain.LoadBalancingFlow;
import net.floodlightcontroller.proactiveloadbalancer.domain.Strategy;
import net.floodlightcontroller.proactiveloadbalancer.domain.Topology;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.*;
import java.util.Map.Entry;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

class ConnectionLoadBalancer {

    // Config
    private Strategy strategy;
    private Topology topology;
    private List<IPv4Address> servers;
    private Map<DatapathId, IPv4Address> vips;
    private Iterator<IPv4Address> roundRobinIterator;

    // Floodlight services
    private IOFSwitchService switchService;

    // Runtime
    private Map<IPv4Address, IPv4Address> clientServerAllocations;
    private Map<IPv4Address, Integer> serverConnectionCounts;
    private Map<IPv4Address, Map<DatapathId, LoadBalancingFlow>> knownPhysicalFlows;

    ConnectionLoadBalancer(Strategy strategy, Topology topology, Map<DatapathId, IPv4Address> vips, IOFSwitchService switchService) {
        this.strategy = strategy;
        this.topology = topology;
        this.servers = topology.getServers();
        this.vips = vips;
        this.roundRobinIterator = Iterators.cycle(servers);

        this.switchService = switchService;

        clientServerAllocations = new HashMap<>();
        serverConnectionCounts = servers.stream().collect(toMap(s -> s, s -> 0));
        servers.forEach(server -> serverConnectionCounts.put(server, 0));
        knownPhysicalFlows = new HashMap<>();
    }

    boolean handlePacketIn(IPv4Address client) {
        synchronized (this) {
            if (!isClientKnown(client)) {
                IPv4Address server = allocateServer(client);
                clientServerAllocations.put(client, server);
                serverConnectionCounts.put(server, serverConnectionCounts.get(server) + 1);

                LoadBalancingFlow logicalFlow = buildLogicalFlow(client, server);
                Map<DatapathId, LoadBalancingFlow> physicalFlows = buildPhysicalFlows(logicalFlow);
                knownPhysicalFlows.put(client, physicalFlows);
                installPhysicalFlows(physicalFlows);
                return true;
            }
        }
        return false;
    }

    void handleFlowRemoved(IPv4Address client, DatapathId switchId) {
        synchronized (this) {
            if (isClientKnown(client)) {
                IPv4Address server = clientServerAllocations.get(client);
                Map<DatapathId, LoadBalancingFlow> physicalFlows = knownPhysicalFlows.get(client);
                physicalFlows.remove(switchId);

                if (physicalFlows.isEmpty()) {
                    clientServerAllocations.remove(client);
                    serverConnectionCounts.put(server, serverConnectionCounts.get(server) - 1);
                    knownPhysicalFlows.remove(client);
                }
            }
        }
    }

    List<LoadBalancingFlow> knownPhysicalFlowsForSwitch(DatapathId switchId) {
        return knownPhysicalFlows.values().stream()
                .map(flows -> flows.get(switchId))
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private boolean isClientKnown(IPv4Address client) {
        return clientServerAllocations.containsKey(client);
    }

    private IPv4Address allocateServer(IPv4Address client) {
        switch (strategy) {
            case round_robin:
                return roundRobinIterator.next();
            case ip_hash:
                return servers.get(client.hashCode() % servers.size());
            case least_conn:
                return serverConnectionCounts.entrySet().stream()
                        .sorted(comparingInt(Entry::getValue))
                        .map(Entry::getKey)
                        .findFirst()
                        .orElse(null);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private LoadBalancingFlow buildLogicalFlow(IPv4Address client, IPv4Address server) {
        return new LoadBalancingFlow(client.withMaskOfLength(32), server);
    }

    private Map<DatapathId, LoadBalancingFlow> buildPhysicalFlows(LoadBalancingFlow logicalFlow) {
        Map<DatapathId, List<LoadBalancingFlow>> physicalFlows = FlowBuilder.buildPhysicalFlows(topology, vips, singletonList(logicalFlow));
        // Extract single flow per switch
        return physicalFlows.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(toMap(
                        e -> e.getKey(),
                        e -> e.getValue().get(0)));
    }

    private void installPhysicalFlows(Map<DatapathId, LoadBalancingFlow> physicalFlows) {
        Concurrently.forEach(topology.getSwitches(), (switchId) -> {
            IOFSwitch iofSwitch = switchService.getActiveSwitch(switchId);
            OFFactory factory = iofSwitch.getOFFactory();
            IPv4Address vip = vips.get(switchId);
            List<LoadBalancingFlow> flows = singletonList(physicalFlows.get(switchId));
            MessageBuilder.addLoadBalancingIngressFlows(switchId, factory, vip, flows, strategy.cookie(), true)
                    .forEach(iofSwitch::write);
        });
    }
}
