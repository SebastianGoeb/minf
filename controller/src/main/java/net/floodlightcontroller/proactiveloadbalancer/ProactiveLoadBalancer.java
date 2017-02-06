package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.proactiveloadbalancer.web.ProactiveLoadBalancerWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.Masked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.floodlightcontroller.proactiveloadbalancer.Strategy.greedy;

public class ProactiveLoadBalancer implements IFloodlightModule, IOFMessageListener, IOFSwitchListener,
        IProactiveLoadBalancerService {

    private static final Logger LOG = LoggerFactory.getLogger(ProactiveLoadBalancer.class);

    // Contants
    // TODO configurable?
    static final IPv4AddressWithMask CLIENT_RANGE = IPv4AddressWithMask.of("10.0.0.0/8");
    // TODO configurable?
    private static final long MEASUREMENT_INTERVAL = 60; // 1 minute
    // TODO configurable?
    private static final double RELATIVE_THRESHOLD = 0.1; // 10% of total traffic

    // Services
    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService switchManager;
    private IRestApiService restApiService;
    private IThreadPoolService threadPoolService;

    // Config
    private IPv4Address vip = null;
    private Strategy strategy = null;
    private Topology topology = null;

    // Traffic measurement information
    private Map<DatapathId, PrefixTrie<Long>> measurements = new HashMap<>();
    private ScheduledFuture<?> collectionFuture;

    // Load balancing information
    private Map<DatapathId, Set<LoadBalancingFlow>> flows = new HashMap<>();

    // Utilities
    private static PrefixTrie<Long> adjustedMeasurement(PrefixTrie<Long> measurement) {
        // Expand leaf nodes above traffic threshold, collapse internal nodes below threshold
        long absoluteThreshold = Math.max(2L, (long) (measurement.getRoot().getValue() * RELATIVE_THRESHOLD));
        measurement.traversePreOrder((node, prefix) -> {
            if (node.getValue() >= absoluteThreshold && node.isLeaf() && prefix.getMask().asCidrMaskLength() < 32) {
                node.expand(node.getValue() / 2L, node.getValue() / 2L + node.getValue() % 2L);
            } else if (node.getValue() < absoluteThreshold && !node.isLeaf() && !node.isRoot()) {
                node.collapse();
            }
        });
        return measurement;
    }

    private static Map<IPv4AddressWithMask, Long> getByteCounts(IOFSwitch ofSwitch, OFFlowStatsRequest statsRequest) {
        Objects.requireNonNull(ofSwitch);

        LOG.info("Getting byte counts from switch {} table {}", ofSwitch.getId(), statsRequest.getTableId());
        List<OFFlowStatsReply> statsReplies;
        try {
            statsReplies = ofSwitch
                    .writeStatsRequest(statsRequest)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.debug("Unable to get byte counts from switch {}", ofSwitch.getId(), e);
            return null;
        }

        // Record prefix and byte count for all flows
        Map<IPv4AddressWithMask, Long> byteCounts = new HashMap<>();
        for (OFFlowStatsReply reply : statsReplies) {
            for (OFFlowStatsEntry entry : reply.getEntries()) {
                Masked<IPv4Address> prefix = getMaskedField(entry.getMatch(), MatchField.IPV4_SRC);
                long byteCount = entry.getByteCount().getValue();
                byteCounts.put(prefix.getValue().withMask(prefix.getMask()), byteCount);
            }
        }
        return byteCounts;
    }

    private static Masked<IPv4Address> getMaskedField(Match match, MatchField<IPv4Address> mf) {
        if (match.isExact(mf)) {
            return Masked.of(match.get(mf), IPv4Address.FULL_MASK);
        } else if (match.isPartiallyMasked(mf)) {
            return match.getMasked(mf);
        } else {
            return IPv4AddressWithMask.NONE;
        }
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

    // Process incoming packets
    @Override
    public Command receive(IOFSwitch iofSwitch, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
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
        if (vip != null && topology != null && strategy != null) {
            boolean switchIsManaged = topology.getBridges().stream().anyMatch(bridge -> bridge.getDpid().equals(dpid));
            if (switchIsManaged) {
                LOG.info("Setting up switch {}", dpid);
                setupSwitch(dpid);
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
    public void setVip(IPv4Address newVip) {
        if (!Objects.equals(vip, newVip)) {
            teardown();
            vip = newVip;
            setup();
        }
    }

    @Override
    public void setTopology(Topology newTopology) {
        if (!Objects.equals(topology, newTopology)) {
            teardown();
            topology = newTopology;
            setup();
        }
    }

    @Override
    public void setStrategy(Strategy newStrategy) {
        if (!Objects.equals(strategy, newStrategy)) {
            teardown();
            strategy = newStrategy;
            setup();
        }
    }

    private void teardown() {
        if (vip != null && topology != null && strategy != null) {
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
            });
        }
    }

    private void setup() {
        if (vip != null && topology != null && strategy != null) {
            LOG.info("Setting up all switches");
            // Init traffic stuff
            initTrafficMeasurement();

            // Init load balancing stuff
            buildFlows();

            // Install flows
            getActiveManagedSwitches().forEach(iofSwitch -> setupSwitch(iofSwitch.getId()));

            // Start measurement cycle
            collectionFuture = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(() -> {
                // Collect traffic measurements
                collectAllMeasurements();

                // Update traffic measurement
                getActiveManagedSwitches().forEach(iofSwitch -> {
                    DatapathId dpid = iofSwitch.getId();
                    OFFactory factory = iofSwitch.getOFFactory();

                    // Delete measurement flows
                    MessageBuilder.deleteMeasurementFlows(dpid, factory)
                            .forEach(iofSwitch::write);

                    // Add new measurement flows
                    PrefixTrie<Long> adjustedMeasurement = adjustedMeasurement(PrefixTrie.copy(measurements.get(dpid)));
                    MessageBuilder.addMeasurementFlows(dpid, factory, adjustedMeasurement)
                            .forEach(iofSwitch::write);
                });

                // Update load balancing
                if (strategy == greedy) {
                    buildFlows();

                    getActiveManagedSwitches().forEach(iofSwitch -> {
                        DatapathId dpid = iofSwitch.getId();
                        OFFactory factory = iofSwitch.getOFFactory();

                        // Delete load balancing flows
                        MessageBuilder.deleteLoadBalancingFlows(dpid, factory)
                                .forEach(iofSwitch::write);

                        // Add new load balancing flows
                        MessageBuilder.addLoadBalancingFlows(dpid, factory, vip, flows.get(dpid))
                                .forEach(iofSwitch::write);
                    });
                }
            }, MEASUREMENT_INTERVAL, MEASUREMENT_INTERVAL, TimeUnit.SECONDS);
        }
    }

    private void setupSwitch(DatapathId dpid) {
        IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
        OFFactory factory = ofSwitch.getOFFactory();

        // Add stub flows
        MessageBuilder.addStubFlows(dpid, factory, vip)
                .forEach(ofSwitch::write);

        // Add measurement fallback flows
        MessageBuilder.addFallbackFlows(dpid, factory)
                .forEach(ofSwitch::write);

        // Add measurement flows
        MessageBuilder.addMeasurementFlows(dpid, factory, measurements.get(dpid))
                .forEach(ofSwitch::write);

        // Add load balancing flows
        MessageBuilder.addLoadBalancingFlows(dpid, factory, vip, flows.get(dpid))
                .forEach(ofSwitch::write);

        // Add forwarding flows
        MessageBuilder.addForwardingFlows(dpid, factory, topology.getForwardingFlows())
                .forEach(ofSwitch::write);
    }

    private void initTrafficMeasurement() {
        measurements.clear();
        for (Bridge bridge : topology.getBridges()) {
            DatapathId dpid = bridge.getDpid();
            measurements.put(dpid, PrefixTrie.inflate(CLIENT_RANGE, 0L, prefix -> prefix.equals(CLIENT_RANGE)));
        }
    }

    private void buildFlows() {
        for (Bridge bridge : topology.getBridges()) {
            DatapathId dpid = bridge.getDpid();
            switch (strategy) {
                case uniform:
                    flows.put(dpid, FlowBuilder.buildFlowsUniform(topology, flows.get(dpid)));
                    break;
                case greedy:
                    // TODO
                    LOG.warn(MessageFormat.format("Unsupported strategy {0}", strategy));
                    PrefixTrie<Long> traffic = measurements.get(dpid);
                    flows.put(dpid, FlowBuilder.buildFlowsGreedy(topology, flows.get(dpid), traffic));
                    break;
                default:
                    LOG.warn(MessageFormat.format("Unsupported strategy {0}", strategy));
                    throw new UnsupportedOperationException();
            }
        }
    }

    private void collectAllMeasurements() {
        LOG.info("Collecting traffic measurements");
        getActiveManagedSwitches().forEach(iofSwitch -> {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();

            // Build stats requests
            OFFlowStatsRequest ingressRequest = MessageBuilder.requestIngressMeasurementFlowStats(dpid, factory);
            OFFlowStatsRequest egressRequest = MessageBuilder.requestEgressMeasurementFlowStats(dpid, factory);

            // Read Stats
            Map<IPv4AddressWithMask, Long> ingressByteCounts = getByteCounts(iofSwitch, ingressRequest);
            Map<IPv4AddressWithMask, Long> egressByteCounts = getByteCounts(iofSwitch, egressRequest);
            if (ingressByteCounts == null || egressByteCounts == null) {
                LOG.warn("Can't retrieve stats from switch {}. Skipping.", dpid);
                return;
            }
            if (!Objects.equals(ingressByteCounts.keySet(), egressByteCounts.keySet())) {
                LOG.warn("Ingress and egress flows for switch {} mismatched. " +
                        "Traffic measurements may be incorrect.", dpid);
            }

            // Inflate measurement tree (one leaf node for each client prefix)
            Set<IPv4AddressWithMask> prefixes = Sets.union(ingressByteCounts.keySet(), egressByteCounts.keySet());
            Queue<IPv4AddressWithMask> prefixesInPreOrder = new PriorityQueue<>(prefixes);
            PrefixTrie<Long> measurement = PrefixTrie.inflate(CLIENT_RANGE, 0L, prefix -> {
                while (prefix.equals(prefixesInPreOrder.peek())) {
                    prefixesInPreOrder.remove();
                }
                return prefixesInPreOrder.peek() != null && prefix.contains(prefixesInPreOrder.peek().getValue());
            });

            // Fill in traffic data
            measurement.traversePostOrder((node, prefix) -> {
                long value = 0;
                if (ingressByteCounts.containsKey(prefix)) {
                    value += ingressByteCounts.get(prefix);
                }
                if (egressByteCounts.containsKey(prefix)) {
                    value += egressByteCounts.get(prefix);
                }
                // Aggregate child traffic as we go up the tree
                if (!node.isLeaf()) {
                    value += node.getChild0().getValue();
                    value += node.getChild1().getValue();
                }
                node.setValue(value);
            });

            // Save traffic data for later use by other modules like load balancer
            measurements.put(dpid, PrefixTrie.copy(measurement));
        });
    }

    private List<IOFSwitch> getActiveManagedSwitches() {
        return topology.getBridges().stream()
                .map(bridge -> bridge.getDpid())
                .map(dpid -> switchManager.getActiveSwitch(dpid))
                .filter(ofSwitch -> ofSwitch != null)
                .collect(Collectors.toList());
    }

    // TODO
    private void newMeasurement() {
        if (vip != null && strategy == Strategy.greedy) {
            // Update flows
            buildFlows();

            // Update switches
        }
    }
}
