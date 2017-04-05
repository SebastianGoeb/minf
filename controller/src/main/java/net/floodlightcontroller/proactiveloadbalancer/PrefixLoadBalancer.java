package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.proactiveloadbalancer.domain.*;
import net.floodlightcontroller.proactiveloadbalancer.util.IPUtil;
import net.floodlightcontroller.proactiveloadbalancer.util.IPv4AddressRange;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.floodlightcontroller.proactiveloadbalancer.domain.Strategy.uniform;

class PrefixLoadBalancer {

    private static final Logger LOG = LoggerFactory.getLogger(PrefixLoadBalancer.class);

    // Config
    private IPv4AddressRange range;
    private Strategy strategy;
    private Config config;
    private Map<DatapathId, IPv4Address> vips;

    // Floodlight services
    private IOFSwitchService switchService;

    // Runtime
    private List<LoadBalancingFlow> logicalFlows;
    private Map<DatapathId, List<LoadBalancingFlow>> physicalFlows;
    private List<Transition> transitions;

    // Transition stuff
    private Map<IPv4Address, Map<DatapathId, LoadBalancingFlow>> knownPhysicalFlows;
    private Set<IPv4Address> knownTransitionClients;

    private long lastUpdate;

    PrefixLoadBalancer(IPv4AddressRange range,
            Strategy strategy,
            Config config,
            Map<DatapathId, IPv4Address> vips,
            IOFSwitchService switchService) {
        this.range = range;
        this.strategy = strategy;
        this.config = config;
        this.vips = vips;

        this.switchService = switchService;

        lastUpdate = Long.MIN_VALUE;

        // Transition stuff
        knownPhysicalFlows = new HashMap<>();
        knownTransitionClients = new HashSet<>();

        // Initial flows
        handleClientMeasurements(null);
    }

    boolean handlePacketIn(IPv4Address client, boolean isNew) {
        synchronized (this) {
            if (!isTransitionClientKnown(client)) {
                Transition transition = transitions.stream()
                        .filter(t -> t.getPrefix().contains(client))
                        .findFirst()
                        .orElse(null);
                IPv4Address server = isNew ? transition.getIpNew() : transition.getIpOld();
                knownTransitionClients.add(client);

                LoadBalancingFlow logicalFlow = buildLogicalFlow(client, server);
//                LOG.info("Logical flow for client {}  : {}", client, logicalFlow);
                Map<DatapathId, LoadBalancingFlow> physicalFlows = buildPhysicalFlows(logicalFlow);
//                LOG.info("Physical flows for client {}: {}", client, physicalFlows);
                knownPhysicalFlows.put(client, physicalFlows);

                // Install flows
                Concurrently.forEach(config.getTopology().getSwitches(), (switchId) -> {
                    IOFSwitch iofSwitch = switchService.getActiveSwitch(switchId);
                    OFFactory factory = iofSwitch.getOFFactory();
                    IPv4Address vip = vips.get(switchId);
                    List<LoadBalancingFlow> flows = singletonList(physicalFlows.get(switchId));
                    MessageBuilder.addLoadBalancingMicroFlows(switchId, factory, vip, flows, U64.ZERO)
                            .forEach(iofSwitch::write);
                });
                return true;
            }
        }
        return false;
    }

    private LoadBalancingFlow buildLogicalFlow(IPv4Address client, IPv4Address server) {
        return new LoadBalancingFlow(client.withMaskOfLength(32), server);
    }

    private Map<DatapathId, LoadBalancingFlow> buildPhysicalFlows(LoadBalancingFlow logicalFlow) {
        Map<DatapathId, List<LoadBalancingFlow>> physicalFlows = FlowBuilder.buildPhysicalFlows(config.getTopology(), vips, singletonList(logicalFlow));
        // Extract single flow per switch
        return physicalFlows.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(toMap(
                        e -> e.getKey(),
                        e -> e.getValue().get(0)));
    }

    void handleFlowRemoved(IPv4Address client, DatapathId switchId) {
        synchronized (this) {
            Map<DatapathId, LoadBalancingFlow> physicalFlows = knownPhysicalFlows.get(client);
            if (physicalFlows == null) {
                LOG.warn("Flow removed for client {}, but no physical flows known.", client);
                return;
            }
            physicalFlows.remove(switchId);

            if (physicalFlows.isEmpty()) {
                knownTransitionClients.remove(client);
                knownPhysicalFlows.remove(client);
            }
        }
    }

    void handleClientMeasurements(Map<DatapathId, List<Measurement>> clientMeasurements) {
        // Only handle if sufficient time has passed
        if (System.currentTimeMillis() > lastUpdate + config.getLoadBalancingInterval() * 1000) {
            lastUpdate = System.currentTimeMillis();
            Topology topology = config.getTopology();

            // Build logical flows
            List<LoadBalancingFlow> logicalFlowsOld = logicalFlows;
            logicalFlows = buildPrefixLogicalFlows(clientMeasurements);

            // Calculate transitions
            if (logicalFlowsOld != null) {
                transitions = DifferenceFinder.findDifferences(logicalFlowsOld, logicalFlows);
                LOG.info("Transitions: {}", transitions);
            } else {
                transitions = emptyList();
            }
            LOG.info("Logical Flows: {}", logicalFlows);

            // Build physical flows
            physicalFlows = FlowBuilder.buildPhysicalFlows(topology, vips, logicalFlows);

            // Install flows
            reinstallPhysicalFlowsInAllSwitches();
        }
    }

    void reinstallPhysicalFlowsInSwitch(DatapathId switchId) {
        Topology topology = config.getTopology();
        long loadBalancingInterval = config.getLoadBalancingInterval();

        IOFSwitch iofSwitch = switchService.getActiveSwitch(switchId);
        OFFactory factory = iofSwitch.getOFFactory();
        IPv4Address vip = vips.get(switchId);

        U64 cookie = strategy.cookie();

        List<LoadBalancingFlow> flows = physicalFlows.get(switchId);
        MessageBuilder.deleteLoadBalancingFlows(switchId, factory, cookie).forEach(iofSwitch::write);
        if (topology.isCoreSwitch(switchId)) {
            int transitionHardTimeout = (int) Math.max(1, loadBalancingInterval - 1);
            MessageBuilder.addLoadBalancingTransitionFlows(switchId, factory, vip, transitions, transitionHardTimeout).forEach(iofSwitch::write);
        }
        MessageBuilder.addLoadBalancingIngressFlows(switchId, factory, vip, flows, cookie).forEach(iofSwitch::write);
    }

    // TODO check this works right!!!
    private List<LoadBalancingFlow> buildPrefixLogicalFlows(Map<DatapathId, List<Measurement>> clientMeasurements) {
        Topology topology = config.getTopology();
        Map<IPv4Address, Double> weights = config.getWeights();
        IPv4AddressRange clientRange = config.getClientRange();

        List<WeightedPrefix> doubleMeasurements = new ArrayList<>();
        if (strategy == uniform || clientMeasurements == null) {
            // Generate uniform measurements
            if (!topology.getServers().isEmpty()) {
                int numberOfServers = topology.getServers().size();
                int bits = 32 - Integer.numberOfLeadingZeros(numberOfServers - 1);
                bits += 2;
                int powerOfTwo = 1 << bits;
                // FIXME don't use base prefix, it might be larger than client range
                IPv4AddressWithMask base = IPUtil.base(range);
                int baseAddr = base.getValue().getInt();
                int maskLength = base.getMask().asCidrMaskLength() + bits;
                int increment = 1 << (32 - maskLength);
                for (int i = 0; i < powerOfTwo; i++) {
                    IPv4AddressWithMask prefix = IPv4Address.of(baseAddr + i * increment).withMaskOfLength(maskLength);
                    doubleMeasurements.add(new WeightedPrefix(prefix, 1));
                }
            }
        } else {
            // Convert long measurements to double measurements
            // TODO remove long measurements eventually
            doubleMeasurements = clientMeasurements.values().stream()
                    .flatMap(Collection::stream)
                    .map(msmt -> new WeightedPrefix(msmt.getPrefix(), msmt.getBytes()))
                    .collect(toList());
        }

        // Combine dips and weights into list of servers
        List<Server> servers = topology.getServers().stream()
                .map(dip -> new Server(dip, weights.get(dip)))
                .collect(toList());

        IPv4AddressWithMask basePrefix = IPUtil.base(range);
        List<WeightedPrefix> mergedMeasurements = MeasurementMerger.merge(doubleMeasurements, clientRange);
        if (mergedMeasurements.isEmpty()) {
            mergedMeasurements = singletonList(
                    new WeightedPrefix(basePrefix, 1));
        }
        double total = mergedMeasurements.stream()
                .mapToDouble(WeightedPrefix::getWeight)
                .sum();
        if (total == 0) {
            mergedMeasurements.forEach(wp -> wp.setWeight(1));
        }

        return GreedyPrefixAssigner.assignPrefixes(basePrefix, mergedMeasurements, servers);
    }

    private void reinstallPhysicalFlowsInAllSwitches() {
        Topology topology = config.getTopology();

        List<DatapathId> switchIds = topology.getSwitches();
        Concurrently.forEach(switchIds, switchId -> {
            if (switchService.getActiveSwitch(switchId) != null) {
                reinstallPhysicalFlowsInSwitch(switchId);
            }
        });
    }

    private boolean isTransitionClientKnown(IPv4Address client) {
        return knownTransitionClients.contains(client);
    }
}
