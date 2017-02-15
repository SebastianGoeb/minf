package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFErrorMsgException;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.proactiveloadbalancer.web.ProactiveLoadBalancerWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
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
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static net.floodlightcontroller.proactiveloadbalancer.Strategy.non_uniform;
import static net.floodlightcontroller.proactiveloadbalancer.Strategy.traditional;
import static net.floodlightcontroller.proactiveloadbalancer.Strategy.uniform;

public class ProactiveLoadBalancer implements IFloodlightModule, IOFMessageListener, IOFSwitchListener,
        IProactiveLoadBalancerService {

    private static final Logger LOG = LoggerFactory.getLogger(ProactiveLoadBalancer.class);

    // Services
    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService switchManager;
    private IRestApiService restApiService;
    private IThreadPoolService threadPoolService;

    // Config
    private Config config = null;

    // Derived config
    private Map<DatapathId, IPv4Address> dVips = new HashMap<>();
    private IPv4Address clientMin;
    private IPv4Address clientMax;

    // Traffic measurement information
    private ScheduledFuture<?> collectionFuture;
    private Map<DatapathId, Collection<Measurement>> measurements;
    private Map<DatapathId, List<LoadBalancingFlow>> lbFlows;

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
        int rand = new Random().nextInt(config.getTopology().getServers().size());
        IPv4Address dip = config.getTopology().getServers().get(rand);
        LoadBalancingFlow flow = new LoadBalancingFlow(ipv4.getSourceAddress().withMaskOfLength(32), dip);

        // install rule from src,vip to server
        // TODO don't install on all switches
        // TODO make sure this doesn't get deleted by prefix-based load balancing update routine
        for (IOFSwitch iofSwitch : getActiveManagedSwitches()) {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            IPv4Address vip = dVips.get(dpid);
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
                IThreadPoolService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchManager = context.getServiceImpl(IOFSwitchService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
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
            if (config.getTopology().getSwitches().contains(dpid)) {
                LOG.info("Setting up switch {}", dpid);
                // TODO
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

            // ---- Derived config stuff ----
            // Assign dvips
            // TODO do this differently?
            for (DatapathId dpid : config.getTopology().getSwitches()) {
                IPv4Address dVip = IPv4Address.of(config.getdVipRange().getValue().getInt() + (int) dpid.getLong());
                dVips.put(dpid, dVip);
            }

            // Calculate total client range
            clientMin = config.getStrategyRanges().stream()
                    .flatMap(range -> Stream.of(range.getMin(), range.getMax()))
                    .min(naturalOrder())
                    .orElse(null);
            clientMax = config.getStrategyRanges().stream()
                    .flatMap(range -> Stream.of(range.getMin(), range.getMax()))
                    .max(naturalOrder())
                    .orElse(null);

            // ---- Initial measurements ----
            measurements = new HashMap<>();
            for (DatapathId dpid : config.getTopology().getSwitches()) {
                measurements.put(dpid, new ArrayList<>());
            }

            // ---- Initial uniform load balancing flows ----
            Map<DatapathId, List<LoadBalancingFlow>> uniformLbFlows = updateLoadBalancingFlows(mergeMeasurements(
                    measurements.values().stream()
                            .flatMap(Collection::stream)
                            .collect(toList())));

            // ---- Initial non-uniform load balancing flows ----
            for (StrategyRange range : config.getStrategyRanges()) {
                if (range.getStrategy() == non_uniform) {
                    // TODO add range info
                    // update lb flows
                    lbFlows = updateLoadBalancingFlows(mergeMeasurements(measurements.values().stream()
                            .flatMap(Collection::stream)
                            .collect(toList())));
                }
            }

            // ---- Install permanent and initial flows ----
            getActiveManagedSwitches().forEach(iofSwitch -> {
                DatapathId dpid = iofSwitch.getId();
                OFFactory factory = iofSwitch.getOFFactory();
                IPv4Address vip = dVips.get(dpid);

                // Add stub flows
                MessageBuilder.addStubFlows(dpid, factory, vip)
                        .forEach(iofSwitch::write);

                // Add measurement fallback flows
                MessageBuilder.addFallbackFlows(dpid, factory)
                        .forEach(iofSwitch::write);

                // Add load balancing egress flows
                MessageBuilder.addLoadBalancingEgressFlows(dpid, factory, vip)
                        .forEach(iofSwitch::write);

                // Install traditional load balancer controller flows
                for (StrategyRange range : config.getStrategyRanges()) {
                    if (range.getStrategy() == uniform) {
                        // TODO use proper uniform method
                        MessageBuilder.addLoadBalancingFlows(dpid, factory, dVips.get(dpid), uniformLbFlows.get(dpid))
                                .forEach(iofSwitch::write);
                    } else if (range.getStrategy() == traditional) {
                        // TODO subsets for hierarchical load balancing?
                        List<IPv4AddressWithMask> prefixes = IPUtils.nonOverlappingPrefixes(range.getMin(), range.getMax());
                        MessageBuilder.addTraditionalLoadBalancingControllerFlows(dpid, factory, vip, prefixes)
                                .forEach(iofSwitch::write);
                    }
                }

                // Add forwarding flows
                MessageBuilder.addForwardingFlows(dpid, factory, calculateForwardingFlows(dpid))
                        .forEach(iofSwitch::write);
            });

            // ---- Write initial non-uniform load balancing flows ----
            updateNonuniformLoadBalancingFlows();

            // ---- Start measurement cycle ----
            collectionFuture = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(() -> {
                measurements = readMeasurements();
                updateMsmtFlows();

                // update load balancing flows
                for (StrategyRange range : config.getStrategyRanges()) {
                    if (range.getStrategy() == non_uniform) {
                        // TODO add range info
                        // update lb flows
                        lbFlows = updateLoadBalancingFlows(mergeMeasurements(measurements.values().stream()
                                .flatMap(Collection::stream)
                                .collect(toList())));
                    }
                }
                updateNonuniformLoadBalancingFlows();
            }, config.getMeasurementInterval(), config.getMeasurementInterval(), TimeUnit.SECONDS);
        }
    }

    private void updateNonuniformLoadBalancingFlows() {
        getActiveManagedSwitches().forEach(iofSwitch -> {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            // TODO only delete nonuniform ones
            MessageBuilder.deleteLoadBalancingFlows(dpid, factory)
                    .forEach(iofSwitch::write);
            MessageBuilder.addLoadBalancingFlows(dpid, factory, dVips.get(dpid), lbFlows.get(dpid))
                    .forEach(iofSwitch::write);
        });
    }

    private void updateMsmtFlows() {
        getActiveManagedSwitches().forEach(iofSwitch -> {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            Collection<IPv4AddressWithMask> flows = updateMeasurementFlows(measurements.get(dpid));
            MessageBuilder.deleteMeasurementFlows(dpid, factory)
                    .forEach(iofSwitch::write);
            MessageBuilder.addMeasurementFlows(dpid, factory, flows)
                    .forEach(iofSwitch::write);
        });
    }

    private List<ForwardingFlow> calculateForwardingFlows(DatapathId dpid) {
        List<ForwardingFlow> forwardingFlows = new ArrayList<>();
        // Downlinks to switches
        config.getTopology().getDownlinksToSwitches().get(dpid).forEach((downstreamDpid, portno) -> {
            IPv4AddressWithMask prefix = dVips.get(downstreamDpid).withMaskOfLength(32);
            forwardingFlows.add(new ForwardingFlow(prefix, portno));
        });
        // Downlinks to servers
        config.getTopology().getDownlinksToServers().get(dpid).forEach((dip, portno) -> {
            IPv4AddressWithMask prefix = dip.withMaskOfLength(32);
            forwardingFlows.add(new ForwardingFlow(prefix, portno));
        });
        // Uplinks to switches
        config.getTopology().getUplinksToSwitches().get(dpid).forEach((upstreamDpid, portno) -> {
            IPv4AddressWithMask prefix = IPUtils.base(clientMin, clientMax);
            forwardingFlows.add(new ForwardingFlow(prefix, portno));
        });
        return forwardingFlows;
    }

    private Map<DatapathId, Collection<Measurement>> readMeasurements() {
        Map<DatapathId, Collection<Measurement>> measurements = new HashMap<>();
        for (IOFSwitch iofSwitch : getActiveManagedSwitches()) {
            measurements.put(iofSwitch.getId(), getByteCounts(iofSwitch));
        }
        return measurements;
    }

    private Collection<IPv4AddressWithMask> updateMeasurementFlows(Collection<Measurement> measurements) {
        Objects.requireNonNull(measurements);

        // Merge into tree
        PrefixTrie<Double> tree = mergeMeasurements(measurements);

        // Expand if above threshold, contract if below
        tree.traversePreOrder((node, prefix) -> {
            if (node.isRoot()) {
                node.expand(node.getValue() / 2, node.getValue() / 2);
            } else if (node.getValue() > config.getMeasurementThreshold() && node.isLeaf() && prefix.getMask().asCidrMaskLength() < 32) {
                node.expand(node.getValue() / 2, node.getValue() / 2);
            } else if (node.getValue() < config.getMeasurementThreshold() && !node.isLeaf() && !node.isRoot()) {
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

        return prefixes;
    }

    // TODO check that this works with null children
    private PrefixTrie<Double> mergeMeasurements(Collection<Measurement> measurements) {
        Objects.requireNonNull(measurements);

        double total = measurements.stream()
                .mapToLong(Measurement::getBytes)
                .sum();

        PrefixTrie<Double> tree = PrefixTrie.empty(IPUtils.base(clientMin, clientMax), 0D);
        Queue<Measurement> measurementsInPreOrder = new PriorityQueue<>(comparing(Measurement::getPrefix));
        measurementsInPreOrder.addAll(measurements);

        // Expand tree, fill in measurements, and propagate estimates down
        tree.traversePreOrder((node, prefix) -> {
            Measurement nextMeasurement = measurementsInPreOrder.peek();
            while (nextMeasurement != null && Objects.equals(prefix, nextMeasurement.getPrefix())) {
                double fraction = nextMeasurement.getBytes() / total;
                node.setValue(node.getValue() + (Double.isNaN(fraction) ? 0 : fraction));
                measurementsInPreOrder.remove();
                nextMeasurement = measurementsInPreOrder.peek();
            }
            if (nextMeasurement != null && prefix.contains(nextMeasurement.prefix.getValue())) {
                IPv4Address minOfRightSubtree = IPUtils.min(IPUtils.subprefix1(prefix));
                IPv4Address maxOfLeftSubtree = IPUtils.max(IPUtils.subprefix0(prefix));
                if (minOfRightSubtree.compareTo(clientMin) <= 0) { // Left subtree (0) not relevant
                    node.expand1(node.getValue());
                } else if (clientMax.compareTo(maxOfLeftSubtree) <= 0) { // Right subtree (1) not relevant
                    node.expand0(node.getValue());
                } else {
                    node.expand(node.getValue() / 2, node.getValue() / 2);
                }
            }
        });
        // Propagate measurements up
        tree.traversePostOrder((node, prefix) -> {
            if (!node.isLeaf()) {
                double val = 0;
                if (node.getChild0() != null) {
                    val += node.getChild0().getValue();
                }
                if (node.getChild1() != null) {
                    val += node.getChild1().getValue();
                }
                node.setValue(val);
            }
        });
        return tree;
    }

    private Map<DatapathId, List<LoadBalancingFlow>> updateLoadBalancingFlows(PrefixTrie<Double> measurementTree) {
        // Topological sort over load balancers (sort by dependency)
        Map<DatapathId, List<DatapathId>> dependencies = new HashMap<>();
        Map<DatapathId, List<DatapathId>> dependents = new HashMap<>();
        for (DatapathId dpid : config.getTopology().getDownlinksToSwitches().keySet()) {
            dependencies.put(dpid, new ArrayList<>());
            dependents.put(dpid, new ArrayList<>());
        }
        for (DatapathId dependent : config.getTopology().getDownlinksToSwitches().keySet()) {
            for (DatapathId dependency : config.getTopology().getDownlinksToSwitches().get(dependent).keySet()) {
                dependencies.get(dependent).add(dependency);
                dependents.get(dependency).add(dependent);
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

        // Build flows
        // TODO different strats, respect previous flows, etc.
        Set<LoadBalancingFlow> allFlows = FlowBuilder.buildFlowsGreedy(config.getWeights(), IPUtils.base(clientMin, clientMax), config.getTopology(), null, measurementTree);

        // Group flows by dip
        Map<IPv4Address, List<LoadBalancingFlow>> flowsByDip = allFlows.stream()
                .collect(groupingBy(LoadBalancingFlow::getDip));

        // Calculate downstream flows for load balancers
        Map<DatapathId, List<LoadBalancingFlow>> flowsByDpid = new HashMap<>();
        for (DatapathId dpid : toposortedDpids) {
            Map<DatapathId, Integer> switches = config.getTopology().getDownlinksToSwitches().get(dpid);
            Map<IPv4Address, Integer> servers = config.getTopology().getDownlinksToServers().get(dpid);
            flowsByDpid.put(dpid, new ArrayList<>());
            // Add flows to servers
            for (IPv4Address dip : servers.keySet()) {
                 flowsByDpid.get(dpid).addAll(flowsByDip.get(dip));
            }
            // Add previously calculated flows to this switch
            for (DatapathId downstreamDpid : switches.keySet()) {
                IPv4Address downstreamVip = dVips.get(downstreamDpid);
                flowsByDpid.get(dpid).addAll(flowsByDpid.get(downstreamDpid).stream()
                        .map(flow -> new LoadBalancingFlow(flow.getPrefix(), downstreamVip))
                        .collect(toList()));
            }
        }

        // TODO group flows

        return flowsByDpid;
    }

    private void setupSwitch(DatapathId dpid) {
        IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
        OFFactory factory = ofSwitch.getOFFactory();
        IPv4Address vip = config.getVip();
        Topology topology = config.getTopology();

//        // Add stub flows
//        MessageBuilder.addStubFlows(dpid, factory, vip)
//                .forEach(ofSwitch::write);
//
//        // Add measurement fallback flows
//        MessageBuilder.addFallbackFlows(dpid, factory)
//                .forEach(ofSwitch::write);
//
//        // Add measurement flows
//        MessageBuilder.addMeasurementFlows(dpid, factory, measurements.get(dpid))
//                .forEach(ofSwitch::write);
//
//        // Add load balancing flows
//        MessageBuilder.addLoadBalancingFlows(dpid, factory, vip, flows.get(dpid))
//                .forEach(ofSwitch::write);
//
//        // Add forwarding flows
//        MessageBuilder.addForwardingFlows(dpid, factory, topology.getForwardingFlows())
//                .forEach(ofSwitch::write);
    }

    private List<IOFSwitch> getActiveManagedSwitches() {
        return config.getTopology().getSwitches().stream()
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
