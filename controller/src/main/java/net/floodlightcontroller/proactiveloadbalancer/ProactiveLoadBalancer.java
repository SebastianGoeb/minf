package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedInts;
import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFErrorMsgException;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.proactiveloadbalancer.web.ProactiveLoadBalancerWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class ProactiveLoadBalancer implements IFloodlightModule, IOFMessageListener, IOFSwitchListener,
        IProactiveLoadBalancerService {

    private static final Logger LOG = LoggerFactory.getLogger(ProactiveLoadBalancer.class);

    // Contants
    // TODO configurable?
    static final IPv4AddressWithMask CLIENT_RANGE = IPv4AddressWithMask.of("10.0.0.0/8");
    private static final IPv4AddressWithMask VIP_RANGE = IPv4AddressWithMask.of("10.5.2.0/24");
    private static final long MEASUREMENT_INTERVAL = 60; // 1 minute
    private static final double RELATIVE_THRESHOLD = 0.1; // 10% of total traffic

    // Services
    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService switchManager;
    private IRestApiService restApiService;
    private IThreadPoolService threadPoolService;
    private ITopologyService topologyService;

    // Config
    private Config config = null;
    private Map<DatapathId, IPv4Address> intermediateVips = new HashMap<>();

    // Traffic measurement information
    private ScheduledFuture<?> collectionFuture;

    // Utilities
    private static List<Measurement> getByteCounts(IOFSwitch ofSwitch) {
        Objects.requireNonNull(ofSwitch);

        DatapathId dpid = ofSwitch.getId();
        OFFactory factory = ofSwitch.getOFFactory();

        // Build stats requests
        OFFlowStatsRequest ingressRequest = MessageBuilder.requestIngressMeasurementFlowStats(dpid, factory);
        OFFlowStatsRequest egressRequest = MessageBuilder.requestEgressMeasurementFlowStats(dpid, factory);

        LOG.info("Getting byte counts from switch {}", dpid);
        List<OFFlowStatsReply> replies = new ArrayList<>();
        try {
            replies.addAll(ofSwitch
                    .writeStatsRequest(ingressRequest)
                    .get());
            replies.addAll(ofSwitch
                    .writeStatsRequest(egressRequest)
                    .get());
        } catch (InterruptedException e) {
            LOG.info("Interruped while getting byte counts from switch {}", dpid);
            return Collections.emptyList();
        } catch (ExecutionException e) {
            LOG.info("Unable to get byte counts from switch {} due to {}", dpid,
                    ((OFErrorMsgException) e.getCause()).getErrorMessage());
            return Collections.emptyList();
        }

        // Record prefix and byte count for all flows
        List<Measurement> measurements = new ArrayList<>();
        for (OFFlowStatsReply reply : replies) {
            for (OFFlowStatsEntry entry : reply.getEntries()) {
                Match match = entry.getMatch();
                IPv4AddressWithMask prefix;
                if (match.isExact(MatchField.IPV4_SRC)) {
                    prefix = match.get(MatchField.IPV4_SRC).withMaskOfLength(32);
                } else if (match.isPartiallyMasked(MatchField.IPV4_SRC)) {
                    Masked<IPv4Address> masked = match.getMasked(MatchField.IPV4_SRC);
                    prefix = masked.getValue().withMask(masked.getMask());
                } else {
                    prefix = IPv4AddressWithMask.of("0.0.0.0/0");
                }
                long byteCount = entry.getByteCount().getValue();
                measurements.add(new Measurement(prefix, byteCount));
            }
        }
        return measurements;
    }

    // ----------------------------------------------------------------
    // - IOFMessageListener methods
    // ----------------------------------------------------------------
    @Override
    public String getName() {
        return ProactiveLoadBalancer.class.getSimpleName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    public Command receive(IOFSwitch iofSwitch, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
                if (EthType.IPv4 == eth.getEtherType()) {
                    IPv4 ipv4 = (IPv4) eth.getPayload();
                    IPv4Address src = ipv4.getSourceAddress();
                    for (StrategyRange range : config.getStrategyRanges()) {
                        if (range.getMin().getInt() < src.getInt() && src.getInt() < range.getMax().getInt()) {
                            switch (range.getStrategy()) {
                                case traditional:
                                    handlePacketTraditional(ipv4);
                                    break;
                            }
                            break;
                        }
                    }
                }
                LOG.info("Packet in");
                break;
            case FLOW_REMOVED:
                LOG.info("Flow removed");
                break;
            default:
                break;
        }
        return Command.STOP;
    }

    private void handlePacketTraditional(IPv4 ipv4) {
        // pick server (random)
        Random random = new Random();
        Host server = config.getTopology().getHosts().stream()
                .skip(random.nextInt(config.getTopology().getHosts().size()))
                .findFirst()
                .orElse(null);
        LoadBalancingFlow flow = new LoadBalancingFlow(ipv4.getSourceAddress().withMaskOfLength(32), server.getDip());

        // install rule from src,vip to server
        // TODO don't install on all switches
        // TODO make sure this doesn't get deleted by prefix-based load balancing update routine
        for (IOFSwitch iofSwitch : getActiveManagedSwitches()) {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            IPv4Address vip = intermediateVips.get(dpid);
            MessageBuilder.addTraditionalLoadBalancingFlow(dpid, factory, vip, flow)
                    .forEach(iofSwitch::write);
        }
    }

    // ----------------------------------------------------------------
    // - IFloodlightModule methods
    // ----------------------------------------------------------------
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableList.of(IProactiveLoadBalancerService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.of(IProactiveLoadBalancerService.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of(IFloodlightProviderService.class, IOFSwitchService.class, IRestApiService.class,
                IThreadPoolService.class, ITopologyService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchManager = context.getServiceImpl(IOFSwitchService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
        topologyService = context.getServiceImpl(ITopologyService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        switchManager.addOFSwitchListener(this);
        restApiService.addRestletRoutable(new ProactiveLoadBalancerWebRoutable());
    }

    // ----------------------------------------------------------------
    // - IOFSwitchListener methods
    // ----------------------------------------------------------------
    @Override
    public void switchAdded(DatapathId dpid) {}

    @Override
    public void switchRemoved(DatapathId dpid) {}

    @Override
    public void switchActivated(DatapathId dpid) {
        if (config != null) {
            boolean switchIsManaged = config.getTopology()
                    .getBridges().stream()
                    .anyMatch(bridge -> bridge.getDpid().equals(dpid));
            if (switchIsManaged) {
                LOG.info("Setting up switch {}", dpid);
//                setupSwitch(dpid);
            }
        }
    }

    @Override
    public void switchPortChanged(DatapathId dpid, OFPortDesc port, PortChangeType type) {}

    @Override
    public void switchChanged(DatapathId dpid) {}

    @Override
    public void switchDeactivated(DatapathId dpid) {}

    // ----------------------------------------------------------------
    // - IProactiveLoadBalancerService methods
    // ----------------------------------------------------------------

    @Override
    public void setConfig(Config newConfig) {
        if (!Objects.equals(config, newConfig)) {
            teardown();
            config = newConfig;
            setup();
        }
    }

    private void teardown() {
        if (config != null) {
            LOG.info("Tearing down all switches");
            collectionFuture.cancel(true);
            // TODO wait for that to complete
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            getActiveManagedSwitches().forEach(iofSwitch -> {
                DatapathId dpid = iofSwitch.getId();
                OFFactory factory = iofSwitch.getOFFactory();

                // Delete stub flows
                MessageBuilder.deleteStubFlows(dpid, factory)
                        .forEach(iofSwitch::write);

                // Delete measurement fallback flows
                MessageBuilder.deleteFallbackFlows(dpid, factory)
                        .forEach(iofSwitch::write);

                // Delete measurement flows
                MessageBuilder.deleteMeasurementFlows(dpid, factory)
                        .forEach(iofSwitch::write);

                // Delete load balancing flows
                MessageBuilder.deleteLoadBalancingFlows(dpid, factory)
                        .forEach(iofSwitch::write);

                // Delete forwarding flows
                MessageBuilder.deleteForwardingFlows(dpid, factory)
                        .forEach(iofSwitch::write);
            });
        }
    }

    private void setup() {
        if (config != null) {
            LOG.info("Setting up all switches");

            // Init traffic stuff
//            initTrafficMeasurement();

            // Init load balancing stuff
//            for (StrategyRange strategyRange : config.getStrategyRanges()) {
//                buildFlows(strategyRange);
//            }

            // TODO assign vips
            for (Bridge bridge : config.getTopology().getBridges()) {
                intermediateVips.put(bridge.getDpid(), IPv4Address.of(VIP_RANGE.getValue().getInt() + (int) bridge.getDpid().getLong()));
            }

            Map<DatapathId, Set<Link>> allLinks = topologyService.getAllLinks();
            // Install flows
            getActiveManagedSwitches().forEach(iofSwitch -> {
                DatapathId dpid = iofSwitch.getId();
                OFFactory factory = iofSwitch.getOFFactory();
                Topology topology = config.getTopology();
                IPv4Address vip = intermediateVips.get(dpid);

                // Add stub flows
                MessageBuilder.addStubFlows(dpid, factory, vip)
                        .forEach(iofSwitch::write);

                // Add measurement fallback flows
                MessageBuilder.addFallbackFlows(dpid, factory)
                        .forEach(iofSwitch::write);

                // Setup load balancing flows (default flows)
                for (StrategyRange range : config.getStrategyRanges()) {
                     switch (range.getStrategy()) {
                         case uniform:
                         case non_uniform:
                             // Setup nothing, really
                             break;
                         case traditional:
                             // Convert min,max to list of prefixes
                             int rangeMin = range.getMin().getInt();
                             int rangeMax = range.getMax().getInt();
                             List<IPv4AddressWithMask> prefixes = new ArrayList<>();
                             Queue<IPv4AddressWithMask> queue = new PriorityQueue<>();
                             queue.add(IPv4AddressWithMask.of("0.0.0.0/0"));
                             while(!queue.isEmpty()) {
                                 IPv4AddressWithMask prefix = queue.poll();
                                 int min = prefix.getValue().getInt();
                                 int max = prefix.getValue().or(prefix.getMask().not()).getInt();
                                 boolean inside = UnsignedInts.compare(rangeMin, min) <= 0 && UnsignedInts.compare(max, rangeMax) <= 0;
                                 boolean outside = UnsignedInts.compare(max, rangeMin) < 0 || UnsignedInts.compare(rangeMax, min) < 0;
                                 if (inside) {
                                     // Contained, save
                                     prefixes.add(prefix);
                                } else if (!outside) {
                                     // Too large, split
                                     queue.add(IPv4Address.of(min).withMaskOfLength(prefix.getMask().asCidrMaskLength() + 1));
                                     queue.add(IPv4Address.of(max).withMaskOfLength(prefix.getMask().asCidrMaskLength() + 1));
                                 }
                             }
                             // Install controller flows
                             MessageBuilder.addTraditionalLoadBalancingControllerFlows(dpid, factory,vip, prefixes)
                                    .forEach(iofSwitch::write);
                             break;
                     }
                }
                MessageBuilder.addLoadBalancingEgressFlows(dpid, factory, vip)
                        .forEach(iofSwitch::write);

                // Add forwarding flows
                List<ForwardingFlow> forwardingFlows = new ArrayList<>();
                if (allLinks.containsKey(dpid)) {
                    for (Link link : allLinks.get(dpid)) {
                        if (!Objects.equals(link.getDst(), dpid) && intermediateVips.containsKey(link.getDst())) {
                            forwardingFlows.add(new ForwardingFlow(intermediateVips.get(link.getDst()).withMask(
                                    IPv4Address.NO_MASK), link.getSrcPort().getPortNumber()));
                        }
                    }
                }
                Bridge bridge = topology.getBridges().stream().filter(br -> br.getDpid().equals(dpid)).findFirst().orElse(null);
                for (IPv4Address dip : bridge.getUpstreamHosts()) {
                    IPv4AddressWithMask prefix = dip.withMask(IPv4Address.NO_MASK);
                    forwardingFlows.add(new ForwardingFlow(prefix, topology.getForwardingFlows().stream()
                            .filter(flow -> flow.getPrefix().equals(prefix))
                            .findFirst()
                            .orElse(null)
                            .getPort()));
                }
                forwardingFlows.add(new ForwardingFlow(CLIENT_RANGE, 1)); // TODO use proper port number, from dependents
                MessageBuilder.addForwardingFlows(dpid, factory, forwardingFlows)
                        .forEach(iofSwitch::write);
            });

            // Start measurement cycle
            collectionFuture = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(() -> {
                // read msmt counters
                Map<DatapathId, Collection<Measurement>> measurements = readMeasurements();
                // update msmt flows
                Map<DatapathId, Collection<IPv4AddressWithMask>> flows = updateMeasurementFlows(measurements);
                // write new msmt flows
                writeMeasurementFlows(flows);

                // merge readings
                List<Measurement> allMeasurements = measurements.values().stream()
                        .flatMap(Collection::stream)
                        .collect(toList());
                PrefixTrie<Double> mergedMeasurements = mergeMeasurements(allMeasurements);
                for (StrategyRange range : config.getStrategyRanges()) {
                    switch (range.getStrategy()) {
                        case non_uniform:
                            // TODO add range info
                            // update lb flows
                            Map<DatapathId, List<LoadBalancingFlow>> lbFlows = updateLoadBalancingFlows(mergedMeasurements);
                            // write new lb flows
                            writeLoadBalancingFlows(lbFlows);
                    }
                }
            }, 0, MEASUREMENT_INTERVAL, TimeUnit.SECONDS);
        }
    }

    private Map<DatapathId, Collection<Measurement>> readMeasurements() {
        Map<DatapathId, Collection<Measurement>> measurements = new HashMap<>();
        for (IOFSwitch iofSwitch : getActiveManagedSwitches()) {
            measurements.put(iofSwitch.getId(), getByteCounts(iofSwitch));
        }
        return measurements;
    }

    private Map<DatapathId, Collection<IPv4AddressWithMask>> updateMeasurementFlows(Map<DatapathId, Collection<Measurement>> measurementsByDpid) {
        Objects.requireNonNull(measurementsByDpid);

        Map<DatapathId, Collection<IPv4AddressWithMask>> prefixesByDpid = new HashMap<>();
        measurementsByDpid.forEach((dpid, measurements) -> {
            // Merge into tree
            PrefixTrie<Double> tree = mergeMeasurements(measurements);

            // Expand if above threshold, contract if below
            tree.traversePreOrder((node, prefix) -> {
                if (node.isRoot()) {
                    node.expand(node.getValue() / 2, node.getValue() / 2);
                } else if (node.getValue() > RELATIVE_THRESHOLD && node.isLeaf() && prefix.getMask().asCidrMaskLength() < 32) {
                    node.expand(node.getValue() / 2, node.getValue() / 2);
                } else if (node.getValue() < RELATIVE_THRESHOLD && !node.isLeaf() && !node.isRoot()) {
                    node.collapse();
                }
            });

            // Extract leaf node prefixes;
            Collection<IPv4AddressWithMask> prefixes = new ArrayList<>();
            tree.traversePostOrder((node, prefix) -> {
                if (node.isLeaf()) {
                    prefixes.add(prefix);
                }
            });
            prefixesByDpid.put(dpid, prefixes);
        });
        return prefixesByDpid;
    }

    private void writeMeasurementFlows(Map<DatapathId, Collection<IPv4AddressWithMask>> prefixes) {
        Objects.requireNonNull(prefixes);

        getActiveManagedSwitches().forEach(iofSwitch -> {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            MessageBuilder.deleteMeasurementFlows(dpid, factory)
                    .forEach(iofSwitch::write);
            MessageBuilder.addMeasurementFlows(dpid, factory, prefixes.get(dpid))
                    .forEach(iofSwitch::write);
        });
    }

    private static PrefixTrie<Double> mergeMeasurements(Collection<Measurement> measurements) {
        Objects.requireNonNull(measurements);

        double total = measurements.stream()
                .mapToLong(Measurement::getBytes)
                .sum();

        PrefixTrie<Double> tree = PrefixTrie.empty(CLIENT_RANGE, 0D);
        Queue<Measurement> measurementsInPreOrder = new PriorityQueue<>(comparing(Measurement::getPrefix));
        measurementsInPreOrder.addAll(measurements);

        // Expand tree, fill in measurements, and propagate estimates down
        tree.traversePreOrder((node, prefix) -> {
            Measurement nextMeasurement = measurementsInPreOrder.peek();
            while (nextMeasurement != null && Objects.equals(prefix, nextMeasurement.getPrefix())) {
                node.setValue(node.getValue() + nextMeasurement.getBytes() / total);
                measurementsInPreOrder.remove();
                nextMeasurement = measurementsInPreOrder.peek();
            }
            if (nextMeasurement != null && prefix.contains(nextMeasurement.prefix.getValue())) {
                node.expand(node.getValue() / 2, node.getValue() / 2);
            }
        });
        // Propagate measurements up
        tree.traversePostOrder((node, prefix) -> {
            if (!node.isLeaf()) {
                node.setValue(node.getChild0().getValue() + node.getChild1().getValue());
            }
        });
        return tree;
    }

    private Map<DatapathId, List<LoadBalancingFlow>> updateLoadBalancingFlows(PrefixTrie<Double> measurementTree) {
        // TODO build flows
        Set<LoadBalancingFlow> allFlows = FlowBuilder.buildFlowsUniform(config.getTopology(), null);

        // Group flows by dip
        Map<IPv4Address, List<LoadBalancingFlow>> flowsByDip = allFlows.stream()
                .collect(groupingBy(LoadBalancingFlow::getDip));

        // Topological sort over load balancers (sort by dependency)
        Map<DatapathId, List<DatapathId>> dependencies = new HashMap<>();
        Map<DatapathId, List<DatapathId>> dependents = new HashMap<>();
        for (Bridge bridge: config.getTopology().getBridges()) {
            dependencies.put(bridge.getDpid(), new ArrayList<>());
            dependents.put(bridge.getDpid(), new ArrayList<>());
        }
        for (Bridge bridge: config.getTopology().getBridges()) {
            DatapathId downstream = bridge.getDpid();
            for (DatapathId upstream : bridge.getUpstreamBridges()) {
                dependencies.get(downstream).add(upstream);
                dependents.get(upstream).add(downstream);
            }
        }
        List<DatapathId> toposortedDpids = new ArrayList<>();
        List<DatapathId> frontier = dependencies.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(e -> e.getKey())
                .collect(toList());
        while(!frontier.isEmpty()) {
            DatapathId dpid = frontier.remove(0);
            toposortedDpids.add(dpid);
            for (DatapathId dependent : dependents.get(dpid)) {
                dependencies.get(dependent).remove(dpid);
                if (dependencies.get(dependent).isEmpty()) {
                    dependencies.remove(dependent);
                    frontier.add(dependent);
                }
            }
            dependents.remove(dpid);
        }

        // Calculate upstream flows for load balancers
        Map<DatapathId, List<LoadBalancingFlow>> upstreamFlows = new HashMap<>();
        for (DatapathId dpid : toposortedDpids) {
            Bridge bridge = config.getTopology().getBridges().stream()
                    .filter(b -> b.getDpid().equals(dpid))
                    .findFirst()
                    .orElse(null);
            Set<IPv4Address> upstreamDips = bridge.getUpstreamHosts();
            Set<DatapathId> upstreamDpids = bridge.getUpstreamBridges();
            List<LoadBalancingFlow> flows = new ArrayList<>();
            for (IPv4Address upstreamDip : upstreamDips) {
                 flows.addAll(flowsByDip.get(upstreamDip));
            }
            for (DatapathId upstreamDpid : upstreamDpids) {
                IPv4Address upstreamVip = intermediateVips.get(upstreamDpid);
                flows.addAll(upstreamFlows.get(upstreamDpid).stream()
                        .map(flow -> new LoadBalancingFlow(flow.getPrefix(), upstreamVip))
                        .collect(toList()));
            }
            upstreamFlows.put(dpid, flows);
        }

        // TODO group flows

        return upstreamFlows;
    }

    private void writeLoadBalancingFlows(Map<DatapathId, List<LoadBalancingFlow>> flows) {
        Objects.requireNonNull(flows);

        getActiveManagedSwitches().forEach(iofSwitch -> {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            MessageBuilder.deleteLoadBalancingFlows(dpid, factory)
                    .forEach(iofSwitch::write);
            MessageBuilder.addLoadBalancingFlows(dpid, factory, intermediateVips.get(dpid), flows.get(dpid))
                    .forEach(iofSwitch::write);
        });
    }

    private void setupSwitch(DatapathId dpid) {
        IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
        OFFactory factory = ofSwitch.getOFFactory();
        IPv4Address vip = config.getVip();
        Topology topology = config.getTopology();

        // Add stub flows
        MessageBuilder.addStubFlows(dpid, factory, vip)
                .forEach(ofSwitch::write);

        // Add measurement fallback flows
        MessageBuilder.addFallbackFlows(dpid, factory)
                .forEach(ofSwitch::write);

        // Add measurement flows
//        MessageBuilder.addMeasurementFlows(dpid, factory, measurements.get(dpid))
//                .forEach(ofSwitch::write);
//
//        // Add load balancing flows
//        MessageBuilder.addLoadBalancingFlows(dpid, factory, vip, flows.get(dpid))
//                .forEach(ofSwitch::write);

        // Add forwarding flows
        MessageBuilder.addForwardingFlows(dpid, factory, topology.getForwardingFlows())
                .forEach(ofSwitch::write);
    }

    private List<IOFSwitch> getActiveManagedSwitches() {
        return config.getTopology().getBridges().stream()
                .map(bridge -> bridge.getDpid())
                .map(dpid -> switchManager.getActiveSwitch(dpid))
                .filter(ofSwitch -> ofSwitch != null)
                .collect(toList());
    }

    private static class Measurement {
        private final IPv4AddressWithMask prefix;
        private final long bytes;

        private Measurement(IPv4AddressWithMask prefix, long bytes) {
            this.prefix = prefix;
            this.bytes = bytes;
        }

        private IPv4AddressWithMask getPrefix() {
            return prefix;
        }

        private long getBytes() {
            return bytes;
        }
    }
}
