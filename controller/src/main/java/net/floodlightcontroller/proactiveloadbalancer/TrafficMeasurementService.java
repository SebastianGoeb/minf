package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
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

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.floodlightcontroller.proactiveloadbalancer.ProactiveLoadBalancer.CLIENT_RANGE;

public class TrafficMeasurementService implements IFloodlightModule, ITrafficMeasurementService, IOFSwitchListener {

    private static final Logger LOG = LoggerFactory.getLogger(TrafficMeasurementService.class);

    // Constants
    // TODO configurable?
    private static final long MEASUREMENT_INTERVAL = 60; // 1 minute
    // TODO configurable?
    private static final double RELATIVE_THRESHOLD = 0.1; // 10% of total traffic

    // Services
    private IOFSwitchService switchManager;
    private IThreadPoolService threadPoolService;

    // Listeners
    private Set<IMeasurementListener> listeners = new HashSet<>();

    // Config
    private boolean enabled = false;
    private Set<DatapathId> dpids = new HashSet<>();

    // Measurements
    private Map<DatapathId, PrefixTrie<Long>> measurements = new HashMap<>();
    private ScheduledFuture<?> collectionFuture;

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

        LOG.warn("Getting byte counts from switch {} table {}", ofSwitch.getId(), statsRequest.getTableId());
        List<OFFlowStatsReply> statsReplies;
        try {
            statsReplies = ofSwitch
                    .writeStatsRequest(statsRequest)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Unable to get byte counts from switch {}", ofSwitch.getId(), e);
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
    // - IFloodlightModule methods
    // ----------------------------------------------------------------
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return Collections.singletonList(ITrafficMeasurementService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.of(ITrafficMeasurementService.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of(IOFSwitchService.class, IThreadPoolService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        switchManager = context.getServiceImpl(IOFSwitchService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        // Services
        switchManager.addOFSwitchListener(this);
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
        LOG.info("Adding measurement flows to switch {}", dpid);
        if (dpids.contains(dpid)) {
            addMeasurementFlows(dpid);
        }
    }

    @Override
    public void switchPortChanged(DatapathId dpid, OFPortDesc port, PortChangeType type) {}

    @Override
    public void switchChanged(DatapathId dpid) {}

    @Override
    public void switchDeactivated(DatapathId dpid) {}

    // ----------------------------------------------------------------
    // - ITrafficMeasurementService methods
    // ----------------------------------------------------------------
    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;

            if (enabled) {
                addAllMeasurementFlows();

                // Start measurement
                collectionFuture = threadPoolService.getScheduledExecutor().scheduleWithFixedDelay(() -> {
                    long startTime = System.currentTimeMillis();
                    collectAllMeasurements();
                    deleteAllMeasurementFlows();
                    addAllMeasurementFlows();
                    if (this.enabled) {
                        dispatchListeners();
                    }
                    LOG.info("Traffic update took {} ms", System.currentTimeMillis() - startTime);
                }, MEASUREMENT_INTERVAL, MEASUREMENT_INTERVAL, TimeUnit.SECONDS);
            } else {
                // stop updates
                collectionFuture.cancel(false); // TODO does this work?
                deleteAllMeasurementFlows();
            }
        }
    }

    @Override
    public void setDpids(Set<DatapathId> dpids) {
        if (enabled) {
            deleteAllMeasurementFlows();
        }
        deleteAllFallbackFlows();

        // Update dpids
        this.dpids = dpids;

        // Reset measurements
        measurements.clear();
        for (DatapathId dpid : dpids) {
            measurements.put(dpid, PrefixTrie.inflate(CLIENT_RANGE, 0L, prefix -> prefix.equals(CLIENT_RANGE)));
        }

        if (enabled) {
            addAllMeasurementFlows();
        }
        addAllFallbackFlows();
    }

    @Override
    public void addMeasurementListener(IMeasurementListener listener) {
        listeners.add(listener);
    }

    @Override
    public PrefixTrie<Long> getMeasurement(DatapathId dpid) throws IllegalStateException {
        if (!enabled) {
            throw new IllegalStateException("Traffic measurement service disabled. No measurements available");
        }
        return measurements.get(dpid);
    }

    // ----------------------------------------------------------------
    // - helper methods
    // ----------------------------------------------------------------
    private void collectAllMeasurements() {
        LOG.info("Collecting traffic measurements");
        for (DatapathId dpid : dpids) {
            IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
            if (ofSwitch != null) {
                // Build stats requests
                OFFactory factory = ofSwitch.getOFFactory();
                OFFlowStatsRequest ingressRequest = MessageBuilder.requestIngressMeasurementFlowStats(dpid, factory);
                OFFlowStatsRequest egressRequest = MessageBuilder.requestEgressMeasurementFlowStats(dpid, factory);

                // Read Stats
                Map<IPv4AddressWithMask, Long> ingressByteCounts = getByteCounts(ofSwitch, ingressRequest);
                Map<IPv4AddressWithMask, Long> egressByteCounts = getByteCounts(ofSwitch, egressRequest);
                if (ingressByteCounts == null || egressByteCounts == null) {
                    LOG.warn("Can't retrieve stats from switch {}. Skipping.", dpid);
                    continue;
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
            }
        }
    }

    private void deleteAllMeasurementFlows() {
        LOG.info("Deleting measurement flows");
        for (DatapathId dpid : dpids) {
            IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
            if (ofSwitch != null) {
                OFFactory factory = ofSwitch.getOFFactory();
                for (OFFlowMod flowMod : MessageBuilder.deleteMeasurementFlows(dpid, factory)) {
                    ofSwitch.write(flowMod);
                }
            }
        }
    }

    private void addAllMeasurementFlows() {
        LOG.info("Adding measurement flows");
        for (DatapathId dpid : dpids) {
            addMeasurementFlows(dpid);
        }
    }

    private void addMeasurementFlows(DatapathId dpid) {
        IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
        if (ofSwitch != null) {
            // Adjust copy of measurement tree for next interval
            PrefixTrie<Long> measurementCopy = adjustedMeasurement(PrefixTrie.copy(measurements.get(dpid)));

            // Add measurement flows
            OFFactory factory = ofSwitch.getOFFactory();
            for (OFFlowMod flowMod : MessageBuilder.addMeasurementFlows(dpid, factory, measurementCopy)) {
                ofSwitch.write(flowMod);
            }
        }
    }

    private void deleteAllFallbackFlows() {
        LOG.info("Deleting fallback flows");
        for (DatapathId dpid : dpids) {
            IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
            if (ofSwitch != null) {
                OFFactory factory = ofSwitch.getOFFactory();
                for (OFFlowMod flowMod : MessageBuilder.deleteFallbackFlows(dpid, factory)) {
                    ofSwitch.write(flowMod);
                }
            }
        }
    }

    private void addAllFallbackFlows() {
        LOG.info("Adding fallback flows");
        for (DatapathId dpid : dpids) {
            IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
            if (ofSwitch != null) {
                OFFactory factory = ofSwitch.getOFFactory();
                for (OFFlowMod flowMod : MessageBuilder.addFallbackFlows(dpid, factory)) {
                    ofSwitch.write(flowMod);
                }
            }
        }
    }

    private void dispatchListeners() {
        for (IMeasurementListener listener : listeners) {
            threadPoolService.getScheduledExecutor().submit(() -> listener.newMeasurement());
        }
    }
}
