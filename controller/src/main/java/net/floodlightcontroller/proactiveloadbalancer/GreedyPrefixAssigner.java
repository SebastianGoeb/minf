package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.LoadBalancingFlow;
import net.floodlightcontroller.proactiveloadbalancer.domain.Server;
import net.floodlightcontroller.proactiveloadbalancer.domain.WeightedPrefix;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

class GreedyPrefixAssigner {
    private static final List<LoadBalancingFlow> DEFAULT_FLOWS = emptyList();

    static List<LoadBalancingFlow> assignPrefixes(List<WeightedPrefix> measurements, List<Server> servers) {
        if (servers.isEmpty()) {
            return DEFAULT_FLOWS;
        } else if (servers.size() == 1) {
            return assignPrefixesToServer(measurements, servers.get(0).getDip());
        }

        List<WeightedPrefix> scaledMeasurements = scaleMeasurementsToTotalOne(measurements);
        List<Server> scaledServers = scaleServersToTotalOne(servers);

        // Assign prefixes to servers
        List<LoadBalancingFlow> flows = new ArrayList<>(measurements.size());
        double cumulativeServerWeight = 0;
        double cumulativeMeasurementWeight = 0;
        for (Server server : scaledServers) {
            cumulativeServerWeight += server.getWeight();
            while (!scaledMeasurements.isEmpty() && cumulativeServerWeight > cumulativeMeasurementWeight + scaledMeasurements.get(0).getWeight() / 2) {
                // Assign prefix to server
                flows.add(new LoadBalancingFlow(scaledMeasurements.get(0).getPrefix(), server.getDip()));
                cumulativeMeasurementWeight += scaledMeasurements.get(0).getWeight();
                scaledMeasurements.remove(0);
            }
        }

        return flows;
    }

    private static List<WeightedPrefix> scaleMeasurementsToTotalOne(List<WeightedPrefix> measurements) {
        double totalWeight = measurements.stream().mapToDouble(WeightedPrefix::getWeight).sum();
        return measurements.stream()
                .map(traffic -> new WeightedPrefix(traffic.getPrefix(), totalWeight > 0
                        ? traffic.getWeight() / totalWeight
                        : 1 / measurements.size()))
                .collect(toList());
    }

    private static List<Server> scaleServersToTotalOne(List<Server> servers) {
        double totalWeight = servers.stream().mapToDouble(Server::getWeight).sum();
        return servers.stream()
                .map(server -> new Server(server.getDip(), totalWeight > 0
                        ? server.getWeight() / totalWeight
                        : 1 / servers.size()))
                .collect(toList());
    }

    private static List<LoadBalancingFlow> assignPrefixesToServer(List<WeightedPrefix> measurements, IPv4Address server) {
        return measurements.stream()
                .map(traffic -> new LoadBalancingFlow(traffic.getPrefix(), server))
                .collect(toList());
    }
}
