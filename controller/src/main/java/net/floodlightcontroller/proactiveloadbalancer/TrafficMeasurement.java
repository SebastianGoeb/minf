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
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static net.floodlightcontroller.proactiveloadbalancer.ProactiveLoadBalancer.SRC_RANGE;

public class TrafficMeasurement implements IFloodlightModule, ITrafficMeasurementService, IOFSwitchListener {

    private static Logger log = LoggerFactory.getLogger(TrafficMeasurement.class);

    // Constants
    private static final int INCOMING_TABLE_ID_OFFSET = 1;
    private static final int OUTGOING_TABLE_ID_OFFSET = 3;
    private static final double DEFAULT_RELATIVE_THRESHOLD = 0.1; // 10% of total traffic
    private static final long INTERVAL = 60; // 1 minute

    // Services
    private IOFSwitchService switchManager;
    private IThreadPoolService threadPoolService;

    // Configurable fields
    private Set<IMeasurementListener> listeners = new HashSet<>();
    private IPv4Address vip = null;

    // Runtime fields
    // TODO Do we even need to store these? They only get sent along to the listeners.
    private Map<DatapathId, BinaryTree<Long>> trafficTrees = new HashMap<>();
    // TODO configurable?
    private double relativeThreshold = DEFAULT_RELATIVE_THRESHOLD;

    // Static utilities
    private static TableId getIncomingTableId(DatapathId dpid) {
        return TableId.of(ProactiveLoadBalancer.getBaseTableId(dpid).getValue() + INCOMING_TABLE_ID_OFFSET);
    }

    private static TableId getOutgoingTableId(DatapathId dpid) {
        return TableId.of(ProactiveLoadBalancer.getBaseTableId(dpid).getValue() + OUTGOING_TABLE_ID_OFFSET);
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

        // Start measurement
        threadPoolService.getScheduledExecutor().scheduleWithFixedDelay(this::updateTraffic, INTERVAL, INTERVAL, TimeUnit.SECONDS);
    }

    // ----------------------------------------------------------------
    // - IOFSwitchListener methods
    // ----------------------------------------------------------------
    @Override
    public void switchAdded(DatapathId dpid) {
    }

    @Override
    public void switchRemoved(DatapathId dpid) {
    }

    @Override
    public void switchActivated(DatapathId dpid) {
        if (trafficTrees.containsKey(dpid)) {
            installFlows(switchManager.getActiveSwitch(dpid), trafficTrees.get(dpid));
        }
    }

    @Override
    public void switchPortChanged(DatapathId dpid, OFPortDesc port, PortChangeType type) {
    }

    @Override
    public void switchChanged(DatapathId dpid) {
    }

    @Override
    public void switchDeactivated(DatapathId dpid) {
    }

    // ----------------------------------------------------------------
    // - ITrafficMeasurementService methods
    // ----------------------------------------------------------------

    @Override
    public void setVip(IPv4Address vip) {
        this.vip = vip;
    }

    @Override
    public void addSwitch(DatapathId dpid) {
        // If already present, ignore
        if (trafficTrees.containsKey(dpid)) {
            log.info("Switch {} already registered. Ignoring request to add.", dpid);
            return;
        }

        // Add switch
        log.info("Adding switch {}.", dpid);
        trafficTrees.put(dpid, BinaryTree.inflate(SRC_RANGE, 0L, prefix -> prefix.equals(SRC_RANGE)));

        // Install rules
        log.info("Installing flows on switch {}", dpid);
        if (switchManager.getActiveSwitch(dpid) != null) {
            installFlows(switchManager.getActiveSwitch(dpid), trafficTrees.get(dpid));
        }
    }

    @Override
    public DatapathId deleteSwitch(DatapathId dpid) {
        // If not present, ignore
        if (!trafficTrees.containsKey(dpid)) {
            log.info("Switch {} not registered. Ignoring request to delete.", dpid);
            return null;
        }

        // Delete switch
        log.info("Deleted switch {}", dpid);
        trafficTrees.remove(dpid);

        // TODO do nothing?
//        // Uninstall rules from switch
//        log.info("Uninstalling rules from switch {}", dpid);
//        if (switchManager.getActiveSwitch(dpid) != null) {
//            installFlows(switchManager.getActiveSwitch(dpid), trafficTrees.get(dpid));
//        }
        return dpid;
    }

    @Override
    public void addMeasurementListener(IMeasurementListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean removeMeasurementListener(IMeasurementListener listener) {
        return listeners.remove(listener);
    }

    // ----------------------------------------------------------------
    // - helper methods
    // ----------------------------------------------------------------
    private void updateTraffic() {
        log.info("Updating traffic measurements");
        for (DatapathId dpid : trafficTrees.keySet()) {
            IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);

            // Skip disconnected switches
            if (ofSwitch == null) {
                continue;
            }

            // Read Stats
            Map<IPv4AddressWithMask, Long> incomingByteCounts;
            Map<IPv4AddressWithMask, Long> outgoingByteCounts;
            try {
                incomingByteCounts = getByteCounts(ofSwitch, getIncomingTableId(ofSwitch.getId()));
                outgoingByteCounts = getByteCounts(ofSwitch, getOutgoingTableId(ofSwitch.getId()));
            } catch (ExecutionException | InterruptedException e) {
                log.warn("Can't retrieve stats from switch {}. Skip.", ofSwitch.getId(), e);
                continue;
            }

            if (!Objects.equals(incomingByteCounts.keySet(), outgoingByteCounts.keySet())) {
                log.warn("Incoming and outgoing flow prefixes for switch {} mismatched. Traffic measurements may be incorrect.", ofSwitch.getId());
            }

            // Inflate traffic tree (leaf node for each src prefix)
            Queue<IPv4AddressWithMask> prefixesInPreOrder = new PriorityQueue<>(Sets.union(incomingByteCounts.keySet(), outgoingByteCounts.keySet()));
            BinaryTree<Long> measurementTree = BinaryTree.inflate(SRC_RANGE, 0L, prefix -> {
                while (prefix.equals(prefixesInPreOrder.peek())) {
                    prefixesInPreOrder.remove();
                }
                return prefixesInPreOrder.peek() != null && prefix.contains(prefixesInPreOrder.peek().getValue());
            });

            // Fill in traffic data
            measurementTree.traversePostOrder((node, prefix) -> {
                long value = 0;
                if (incomingByteCounts.containsKey(prefix)) {
                    value += incomingByteCounts.get(prefix);
                }
                if (outgoingByteCounts.containsKey(prefix)) {
                    value += outgoingByteCounts.get(prefix);
                }
                // Aggregate child traffic as we go up the tree
                if (!node.isLeaf()) {
                    value += node.getChild0().getValue();
                    value += node.getChild1().getValue();
                }
                node.setValue(value);
            });

            // Save traffic data for later use by other modules like load balancer
            trafficTrees.put(dpid, BinaryTree.copy(measurementTree));

            // Dispatch listeners
            for (IMeasurementListener listener : listeners) {
                // TODO Should we really use the thread pool? Maybe send all measurements in batch for more intelligent processing?
                threadPoolService.getScheduledExecutor().submit(() -> listener.newMeasurement(trafficTrees.get(dpid)));
            }

            // Expand leaf nodes above traffic threshold, collapse internal nodes below
            long absoluteThreshold = (long) (measurementTree.getRoot().getValue() * relativeThreshold);
            measurementTree.traversePreOrder((node, prefix) -> {
                if (node.getValue() >= absoluteThreshold && node.isLeaf()) {
                    node.expand(node.getValue() / 2);
                } else if (node.getValue() < absoluteThreshold && !node.isLeaf()) {
                    node.collapse();
                }
            });

            // Install flows for next interval
            installFlows(ofSwitch, measurementTree);
        }
    }

    private void installFlows(IOFSwitch ofSwitch, BinaryTree<Long> measurementTree) {
        Objects.requireNonNull(ofSwitch);

        DatapathId dpid = ofSwitch.getId();
        log.info("Install measurement flows on switch {}", dpid);

        // Uninstall previous flows
        ofSwitch.write(ofSwitch
            .getOFFactory()
            .buildFlowDelete()
            .setTableId(getIncomingTableId(dpid))
            .build());

        ofSwitch.write(ofSwitch
            .getOFFactory()
            .buildFlowDelete()
            .setTableId(getOutgoingTableId(dpid))
            .build());

        // Install new flows
        // TODO Install bypass?
        measurementTree.traversePreOrder((node, prefix) -> {
            if (node.isLeaf()) {
                FlowBuilder.addIncomingTrafficMeasurement(ofSwitch, getIncomingTableId(dpid), prefix, vip);
                FlowBuilder.addOutgoingTrafficMeasurement(ofSwitch, getOutgoingTableId(dpid), prefix, vip);
            }
        });
    }

    private static Map<IPv4AddressWithMask, Long> getByteCounts(IOFSwitch ofSwitch, TableId tableId) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(ofSwitch);

        // Request stats
        OFFlowStatsRequest statsRequest = ofSwitch
            .getOFFactory()
            .buildFlowStatsRequest()
            .setTableId(tableId)
            .build();

        List<OFFlowStatsReply> statsReplies = ofSwitch
            .writeStatsRequest(statsRequest)
            .get();

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
}
