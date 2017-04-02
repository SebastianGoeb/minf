package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFErrorMsgException;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.proactiveloadbalancer.domain.*;
import net.floodlightcontroller.proactiveloadbalancer.util.IPUtil;
import net.floodlightcontroller.proactiveloadbalancer.util.IPv4AddressRange;
import net.floodlightcontroller.proactiveloadbalancer.web.ProactiveLoadBalancerWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.projectfloodlight.openflow.protocol.match.MatchField.IPV4_SRC;

public class ProactiveLoadBalancer implements IFloodlightModule, IOFMessageListener, IOFSwitchListener,
        IProactiveLoadBalancerService {

    private static final Logger LOG = LoggerFactory.getLogger(ProactiveLoadBalancer.class);

    // Constants
    // TODO configurable?
    private static final long SNAPSHOT_INTERVAL = 1;

    // Services
    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService switchManager;
    private IRestApiService restApiService;
    private IThreadPoolService threadPoolService;

    // Config
    private Config config = null;

    // Derived config
    private Map<DatapathId, IPv4Address> vips = new HashMap<>();
    private Map<IPv4AddressRange, ConnectionLoadBalancer> connectionLoadBalaners;

    // Scheduling
    private ScheduledFuture<?> clientMeasurementFuture;
    private ScheduledFuture<?> snapshotFuture;

    // Measurement
    private Map<DatapathId, List<Measurement>> clientMeasurements;
    private LinkedList<Snapshot> snapshotHistory;

    // Derived from measurements
    private Map<IPv4Address, Double> serverRates;

    // Flows
    private Map<IPv4AddressRange, List<LoadBalancingFlow>> logicalLoadBalancingFlows; // TODO move to sub-load balancers?
    private List<Transition> transitions;
    private Map<IPv4AddressRange, Map<DatapathId, List<LoadBalancingFlow>>> physicalLoadBalancingFlows; // TODO move to sub-load balancers?
    private Map<IPv4Address, Long> knownTransitionClientsWithTimestamp;
    private static final int FLAG_SYN = 0x2;


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
        DatapathId switchId = iofSwitch.getId();
        switch (msg.getType()) {
            case PACKET_IN:
                OFPacketIn packetIn = (OFPacketIn) msg;
                Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
                if (EthType.IPv4 == eth.getEtherType()) {
                    IPv4 ipv4 = (IPv4) eth.getPayload();
                    IPv4Address client = ipv4.getSourceAddress();
                    if (config != null) {
                        IPv4AddressRange range = config.getStrategyRanges().keySet().stream()
                                .filter(r -> r.contains(client))
                                .findFirst()
                                .orElse(null);
                        if (range != null) {
                            Strategy strategy = config.getStrategyRanges().get(range);
                            if (strategy.isPrefixBased()) {
                                handleClientTransition(client, ipv4);
                            } else {
                                boolean handled = connectionLoadBalaners.get(range).handlePacketIn(client);
                                if (handled) {
                                    iofSwitch.write(iofSwitch.getOFFactory().barrierRequest());
                                    resendPacket(iofSwitch, packetIn);
                                }
                            }
                        }
                    }
                }
                break;
            case FLOW_REMOVED:
                Match match = ((OFFlowRemoved) msg).getMatch();
                if (match.isExact(IPV4_SRC)) {
                    IPv4Address client = match.get(IPV4_SRC);
                    if (config != null) {
                        IPv4AddressRange range = config.getStrategyRanges().keySet().stream()
                                .filter(r -> r.contains(client))
                                .findFirst()
                                .orElse(null);
                        if (range != null) {
                            Strategy strategy = config.getStrategyRanges().get(range);
                            if (strategy.isPrefixBased()) {
                                handleFlowRemovedTransition(client);
                            } else {
                                connectionLoadBalaners.get(range).handleFlowRemoved(client, switchId);
                            }
                        }
                    }
                }
                break;
            default:
                break;
        }
        return Command.STOP;
    }

    private void resendPacket(IOFSwitch iofSwitch, OFPacketIn packetIn) {
        OFFactory factory = iofSwitch.getOFFactory();
        OFActions actions = factory.actions();

        OFPacketOut.Builder builder = factory.buildPacketOut()
                .setActions(singletonList(actions.output(OFPort.TABLE, Integer.MAX_VALUE)))
                .setBufferId(packetIn.getBufferId())
                .setInPort(packetIn.getMatch().get(MatchField.IN_PORT));

        if (builder.getBufferId() == OFBufferId.NO_BUFFER) {
            builder.setData(packetIn.getData());
        }

        iofSwitch.write(builder.build());
    }

    private void handleFlowRemovedTransition(IPv4Address client) {
        if (knownTransitionClientsWithTimestamp.remove(client) == null) {
            LOG.warn("No known transitioning client {} (attempted to remove)", client);
        } else {
            knownTransitionClientsWithTimestamp.remove(client);
        }
    }

    private void handleClientTransition(IPv4Address client, IPv4 ipv4) {
        // Ignore known clients
        if (knownTransitionClientsWithTimestamp.containsKey(client)) {
            return;
        }

        // Add to known clients
        long timestamp = System.currentTimeMillis();
        knownTransitionClientsWithTimestamp.put(client, timestamp);

        // Find relevant transition
        Transition transition = transitions.stream()
                .filter(t -> t.getPrefix().contains(client))
                .findFirst()
                .orElse(null);

        // Determine appropriate server for client
        boolean isNewClient = ipv4.getProtocol() != IpProtocol.TCP || (((TCP) ipv4.getPayload()).getFlags() & FLAG_SYN) != 0;

        IPv4Address server = isNewClient ? transition.getIpNew() : transition.getIpOld();
        LoadBalancingFlow logicalFlow = new LoadBalancingFlow(client.withMaskOfLength(32), server);

        // Route client through network
        Map<DatapathId, List<LoadBalancingFlow>> physicalFlows = FlowBuilder.buildPhysicalFlows(config.getTopology(),
                vips, singletonList(logicalFlow)


        );
        Concurrently.forEach(config.getTopology().getSwitches(), (dpid) -> {
            IOFSwitch iofSwitch = switchManager.getActiveSwitch(dpid);
            OFFactory factory = iofSwitch.getOFFactory();
            IPv4Address vip = vips.get(dpid);
            List<LoadBalancingFlow> flows = physicalFlows.get(dpid);
            MessageBuilder.addLoadBalancingIngressFlows(dpid, factory, vip, flows, U64.ZERO, true)
                    .forEach(iofSwitch::write);
        });

        // TODO Packet out?
//        DatapathId dpid = physicalFlows.entrySet().stream()
//                .filter(e -> !e.getValue().isEmpty())
//                .filter(e -> e.getValue().get(0).getDip().equals(logicalFlow.getDip()))
//                .map(e -> e.getKey())
//                .findFirst()
//                .orElse(null);
//        ipv4.setDestinationAddress(logicalFlow.getDip());
//        eth.setSourceMACAddress(MessageBuilder.SWITCH_MAC);
//        eth.setDestinationMACAddress(MessageBuilder.SERVER_MACS.get(server));
//        OFPort outPort = OFPort.of(config.getTopology().getDownlinksToServers().get(dpid).get(server));
//        IOFSwitch iofSwitch = switchManager.getActiveSwitch(dpid);
//        OFFactory factory = iofSwitch.getOFFactory();
//        iofSwitch.write(factory.buildPacketOut()
//                .setData(eth.serialize())
//                .setActions(singletonList(factory.actions().output(outPort, 0xffFFffFF)))
//                .setInPort(OFPort.CONTROLLER)
//                .build());
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
        if (config != null && config.getTopology().getSwitches().contains(dpid)) {
            LOG.info("Setting up switch {}", dpid);
            IOFSwitch iofSwitch = switchManager.getActiveSwitch(dpid);
            writePermanentFlows(singletonList(dpid));
            writeClientMeasurementFlows(singletonList(iofSwitch));
            writeLoadBalancingFlows(singletonList(dpid), config.getStrategyRanges().keySet());
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
            if (clientMeasurementFuture != null) {
                clientMeasurementFuture.cancel(true);
            }
            if (snapshotFuture != null) {
                snapshotFuture.cancel(true);
            }
            // TODO wait for that to complete
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            getActiveManagedSwitches().forEach(iofSwitch -> {
                DatapathId dpid = iofSwitch.getId();
                OFFactory factory = iofSwitch.getOFFactory();

                // Delete all flows
                MessageBuilder.deleteAllFlows(dpid, factory).forEach(iofSwitch::write);
            });
        }
    }

    private void setup() {
        if (config != null) {
            List<DatapathId> activeDpids = getActiveManagedSwitches()
                    .stream()
                    .map(IOFSwitch::getId)
                    .collect(toList());
            LOG.info("Setting up switches " + Joiner.on(", ").join(activeDpids));

            Topology topology = config.getTopology();
            List<DatapathId> switches = topology.getSwitches();
            Map<IPv4AddressRange, Strategy> strategyRanges = config.getStrategyRanges();

            // Initialize vips
            // TODO assign vips more intelligently?
            int vipBase = config.getdVipRange().getValue().getInt();
            for (DatapathId dpid : switches) {
                if (topology.isCoreSwitch(dpid)) {
                    vips.put(dpid, config.getVip());
                } else {
                    vips.put(dpid, IPv4Address.of(vipBase + (int) dpid.getLong()));
                }
            }

            // Initialize connection load balancers
            connectionLoadBalaners = new HashMap<>();
            for (IPv4AddressRange range : strategyRanges.keySet()) {
                Strategy strategy = strategyRanges.get(range);
                if (!strategy.isPrefixBased()) {
                    connectionLoadBalaners.put(range, new ConnectionLoadBalancer(strategy, topology, vips, switchManager));
                    // TODO initialize here instead?
                }
            }

            // Initialize permanent flows
            writePermanentFlows(getActiveManagedSwitchIds());

            // Initialize client measurements
            clientMeasurements = switches.stream().collect(toMap(dpid -> dpid, dpid -> emptyList()));
            if (config.hasPrefixBasedStrategyRange()) {
                writeClientMeasurementFlows(getActiveManagedSwitches());
            }

            // Initialize snapshots
            snapshotHistory = new LinkedList<>();
            serverRates = topology.getServers().stream()
                    .collect(toMap(server -> server, server -> 0D));

            // Initialize load balancing flows
            logicalLoadBalancingFlows = new HashMap<>();
            transitions = emptyList();
            physicalLoadBalancingFlows = new HashMap<>();
            knownTransitionClientsWithTimestamp = new ConcurrentHashMap<>();
            updatePrefixLoadBalancing();
            writeLoadBalancingFlows(getActiveManagedSwitchIds(), strategyRanges.keySet());

            // Start snapshot cycle
            snapshotFuture = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(() -> {
                try {
                    Snapshot snapshot = getSnapshot();
                    snapshotHistory.addFirst(snapshot);
                    snapshotHistory.removeIf(snap -> snapshot.getTimestamp() - snap.getTimestamp() > TimeUnit.SECONDS.toMillis(config.getMeasurementInterval()));
                    serverRates = calculateRatesAsMovingAverage(config.getServerMeasurementInterval() * 1000);
                    LOG.info("snapshot: {}", snapshot.toJson());
                } catch (Exception e) {
                    // Prevent any exceptions from bubbling up and killing our future
                    e.printStackTrace();
                }
            }, 0, SNAPSHOT_INTERVAL, TimeUnit.SECONDS);

            // Start client prefix measurement cycle
            if (config.hasPrefixBasedStrategyRange()) {
                clientMeasurementFuture = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(() -> {
                    try {
                        updateClientMeasurements();
                        writeClientMeasurementFlows(getActiveManagedSwitches());
                        updatePrefixLoadBalancing();
                        writeLoadBalancingFlows(getActiveManagedSwitchIds(), config.getPrefixBasedStrategyRanges());
                    } catch (Exception e) {
                        // Prevent any exceptions from bubbling up and killing our future
                        e.printStackTrace();
                    }
                }, config.getMeasurementInterval(), config.getMeasurementInterval(), TimeUnit.SECONDS);
            }
        }
    }

    // Prefix LB stuff
    private void updatePrefixLoadBalancing() {
        Map<IPv4AddressRange, Strategy> strategyRanges = config.getStrategyRanges();
        List<IPv4AddressRange> prefixBasedStrategyRanges = config.getPrefixBasedStrategyRanges();

        for (IPv4AddressRange range : prefixBasedStrategyRanges) {
            Strategy strategy = strategyRanges.get(range);

            // Build logical flows
            List<LoadBalancingFlow> logicalFlowsOld = logicalLoadBalancingFlows.get(range);
            List<LoadBalancingFlow> logicalFlowsNew = buildPrefixLogicalFlows(range);
            logicalLoadBalancingFlows.put(range, logicalFlowsNew);

            // Calculate transitions
            if (logicalFlowsOld != null) {
                transitions = DifferenceFinder.findDifferences(logicalFlowsOld, logicalFlowsNew);
            }

            // Build physical flows
            Map<DatapathId, List<LoadBalancingFlow>> physicalFlows = FlowBuilder.buildPhysicalFlows(
                    config.getTopology(), vips, logicalFlowsNew
            );
            physicalLoadBalancingFlows.put(range, physicalFlows);
        }
    }

    private List<LoadBalancingFlow> buildPrefixLogicalFlows(IPv4AddressRange strategyRange) {
        List<WeightedPrefix> doubleMeasurements = new ArrayList<>();
        if (config.isIgnoreMeasurements()) {
            // Generate uniform measurements
            if (!config.getTopology().getServers().isEmpty()) {
                int numberOfServers = config.getTopology().getServers().size();
                int bits = 32 - Integer.numberOfLeadingZeros(numberOfServers - 1);
                bits += 2;
                int powerOfTwo = 1 << bits;
                // FIXME don't use base prefix, it might be larger than client range
                IPv4AddressWithMask base = IPUtil.base(config.getClientRange());
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
        List<Server> servers = config.getTopology().getServers().stream()
                .map(dip -> new Server(dip, config.getWeights().get(dip)))
                .collect(toList());

        IPv4AddressWithMask basePrefix = IPUtil.base(strategyRange);
        List<WeightedPrefix> mergedMeasurements = MeasurementMerger.merge(doubleMeasurements, config.getClientRange());
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

    private void writeLoadBalancingFlows(Collection<DatapathId> switchIds, Iterable<IPv4AddressRange> ranges) {
        Map<IPv4AddressRange, Strategy> strategyRanges = config.getStrategyRanges();

        Concurrently.forEach(switchIds, switchId -> {
            IOFSwitch iofSwitch = switchManager.getActiveSwitch(switchId);
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            IPv4Address vip = vips.get(dpid);

            for (IPv4AddressRange range : ranges) {
                Strategy strategy = strategyRanges.get(range);
                U64 cookie = strategy.cookie();

                if (strategy.isPrefixBased()) {
                    List<LoadBalancingFlow> flows = physicalLoadBalancingFlows.get(range).get(dpid);
                    MessageBuilder.deleteLoadBalancingFlows(dpid, factory, cookie).forEach(iofSwitch::write);
                    if (config.getTopology().isCoreSwitch(dpid)) {
                        MessageBuilder.addLoadBalancingTransitionFlows(dpid, factory, vip, transitions,
                                (int) (config.getMeasurementInterval() / 2)).forEach(iofSwitch::write);
                    }
                    MessageBuilder.addLoadBalancingIngressFlows(dpid, factory, vip, flows, cookie, true).forEach(iofSwitch::write);
                } else {
                    List<LoadBalancingFlow> flows = connectionLoadBalaners.get(range).knownPhysicalFlowsForSwitch(switchId);
                    MessageBuilder.addLoadBalancingIngressFlows(dpid, factory, vip, flows, cookie, false).forEach(iofSwitch::write);
                }
            }
        });
    }

    // Permanent flow stuff
    private void writePermanentFlows(Collection<DatapathId> switchIds) {
        Concurrently.forEach(switchIds, switchId -> {
            IOFSwitch iofSwitch = switchManager.getActiveSwitch(switchId);
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            IPv4Address vip = vips.get(dpid);

            // Add stub flows
            MessageBuilder.addStubFlows(dpid, factory, vip).forEach(iofSwitch::write);

            // Add measurement fallback flows
            MessageBuilder.addFallbackFlows(dpid, factory).forEach(iofSwitch::write);

            // Add load balancing egress flows
            MessageBuilder.addLoadBalancingEgressFlows(dpid, factory, vip).forEach(iofSwitch::write);

            // Install traditional load balancer controller flows
            Map<IPv4AddressRange, Strategy> strategyRanges = config.getStrategyRanges();
            List<IPv4AddressRange> connectionBasedStrategyRanges = config.getConnectionBasedStrategyRanges();
            for (IPv4AddressRange range : connectionBasedStrategyRanges) {
                Strategy strategy = strategyRanges.get(range);
                // TODO merge for hierarchical load balancing?
                List<IPv4AddressWithMask> prefixes = IPUtil.nonOverlappingPrefixes(range);
                MessageBuilder.addLoadBalancingControllerFlows(dpid, factory, vip, prefixes).forEach(iofSwitch::write);
            }

            // Add forwarding flows
            List<ForwardingFlow> physicalFordwardingFlows = FlowBuilder.buildForwardingFlows(dpid, config, vips);
            MessageBuilder.addForwardingFlows(dpid, factory, physicalFordwardingFlows).forEach(iofSwitch::write);
        });
    }

    // Measurement stuff
    private void updateClientMeasurements() {
        if (config.getMeasurementCommands() == null) {
            clientMeasurements = getClientMeasurements(getActiveManagedSwitches());
        } else {
            clientMeasurements = getClientMeasurementsFromSsh(config.getTopology().getSwitches());
        }
    }

    private void writeClientMeasurementFlows(Iterable<IOFSwitch> switches) {
        // TODO parallelize?
        for (IOFSwitch iofSwitch : switches) {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            List<IPv4AddressWithMask> flows = FlowBuilder.buildMeasurementFlows(clientMeasurements.get(dpid), config);

            MessageBuilder.deleteMeasurementFlows(dpid, factory).forEach(iofSwitch::write);
            MessageBuilder.addMeasurementFlows(dpid, factory, vips.get(dpid), flows).forEach(iofSwitch::write);
        }
    }

    private static Map<DatapathId, List<Measurement>> getClientMeasurements(Iterable<IOFSwitch> switches) {
        Objects.requireNonNull(switches);

        // TODO parallelize?
        Map<DatapathId, List<Measurement>> newMeasurements = new HashMap<>();
        for (IOFSwitch iofSwitch : switches) {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();

            // Build stats requests
            OFFlowStatsRequest ingressRequest = MessageBuilder.requestIngressMeasurementFlowStats(dpid, factory);
            OFFlowStatsRequest egressRequest = MessageBuilder.requestEgressMeasurementFlowStats(dpid, factory);

            LOG.info("Getting byte counts from switch {}", dpid);
            List<OFFlowStatsReply> replies = new ArrayList<>();
            try {
                replies.addAll(iofSwitch
                        .writeStatsRequest(ingressRequest)
                        .get());
                replies.addAll(iofSwitch
                        .writeStatsRequest(egressRequest)
                        .get());
            } catch (InterruptedException e) {
                LOG.info("Interruped while getting byte counts from switch {}", dpid);
                continue;
            } catch (ExecutionException e) {
                LOG.info("Unable to get byte counts from switch {} due to {}", dpid,
                        ((OFErrorMsgException) e.getCause()).getErrorMessage());
                continue;
            }
            LOG.info("# of stats replies: {}", replies.size());

            // Record prefix and byte count for all flows
            List<Measurement> parsedMeasurements = new ArrayList<>();
            for (OFFlowStatsReply reply : replies) {
                for (OFFlowStatsEntry entry : reply.getEntries()) {
                    Match match = entry.getMatch();
                    IPv4AddressWithMask prefix;
                    if (match.isExact(IPV4_SRC)) {
                        prefix = match.get(IPV4_SRC).withMaskOfLength(32);
                    } else if (match.isPartiallyMasked(IPV4_SRC)) {
                        Masked<IPv4Address> masked = match.getMasked(IPV4_SRC);
                        prefix = masked.getValue().withMask(masked.getMask());
                    } else {
                        prefix = IPv4AddressWithMask.of("0.0.0.0/0");
                    }
                    long byteCount = entry.getByteCount().getValue();
                    parsedMeasurements.add(new Measurement(prefix, byteCount));
                }
            }
            LOG.info("Measurements: {}", Joiner.on(", ").join(parsedMeasurements));
            newMeasurements.put(dpid, parsedMeasurements);
        }
        return newMeasurements;
    }

    private Map<DatapathId, List<Measurement>> getClientMeasurementsFromSsh(Collection<DatapathId> dpids) {
        Objects.requireNonNull(dpids);

        return Concurrently.forEach(dpids, dpid -> {
            IOFSwitch iofSwitch = switchManager.getActiveSwitch(dpid);

            MeasurementCommand command = config.getMeasurementCommands().get(dpid);
            try {
                Process p = new ProcessBuilder("ssh", command.getEndpoint(), command.getCommand())
                        .redirectError(new File("/dev/null"))
                        .start();
                // Wait for response
                p.waitFor(2, TimeUnit.SECONDS);
                // If taking too long, kill
                if (p.isAlive()) {
                    p.destroyForcibly().waitFor();
                    LOG.warn("SSH TERMINATED. Command was: ssh \"{}\" \"{}\"", command.getEndpoint(), command.getCommand());
                    return emptyList();
                }
                if (p.exitValue() != 0) {
                    LOG.warn("ssh exited unsuccessfully. Return code: {}", p.exitValue());
                } else {
                    String sshStdOut = CharStreams.toString(new InputStreamReader(p.getInputStream()));
                    List<Flow> flows = SshParser.parseResult(sshStdOut);
                    return extractClientMeasurements(flows, dpid);
                }
            } catch (InterruptedException | IOException e) {
                LOG.warn("Unable to ssh into switch {}", iofSwitch.getId());
            }
            return emptyList();
        });
    }

    private Map<DatapathId, List<Measurement>> getServerMeasurementsFromSsh(Collection<DatapathId> dpids) {
        Objects.requireNonNull(dpids);

        HashSet<IPv4Address> servers = new HashSet<>(config.getTopology().getServers());

        return Concurrently.forEach(dpids, dpid -> {
            IOFSwitch iofSwitch = switchManager.getActiveSwitch(dpid);

            MeasurementCommand command = config.getMeasurementCommands().get(dpid);
            try {
                Process p = new ProcessBuilder("ssh", command.getEndpoint(), command.getCommand()).redirectError(new File("/dev/null")).start();
                p.waitFor();
                if (p.exitValue() != 0) {
                    LOG.warn("ssh exited unsuccessfully. Return code: {}", p.exitValue());
                } else {
                    String sshStdOut = CharStreams.toString(new InputStreamReader(p.getInputStream()));
                    List<Flow> flows = SshParser.parseResult(sshStdOut);
                    return extractServerMeasurements(flows, dpid, servers);
                }
            } catch (InterruptedException | IOException e) {
                LOG.warn("Unable to ssh into switch {}", iofSwitch.getId());
            }
            return emptyList();
        });
    }

    // Snapshot stuff
    private Snapshot getSnapshot() {
        long timestamp = System.currentTimeMillis();

        Collection<DatapathId> dpids = config.getTopology().getSwitches();
        HashSet<IPv4Address> servers = new HashSet<>(config.getTopology().getServers());

        Map<DatapathId, List<Flow>> flows = Concurrently.forEach(dpids, (dpid) -> {
            IOFSwitch iofSwitch = switchManager.getActiveSwitch(dpid);

            MeasurementCommand command = config.getMeasurementCommands().get(dpid);
            try {
                Process p = new ProcessBuilder("ssh", command.getEndpoint(), command.getCommand()).redirectError(new File("/dev/null")).start();
                String sshStdOut = CharStreams.toString(new InputStreamReader(p.getInputStream()));
                p.waitFor();
                if (p.exitValue() == 0) {
                    return SshParser.parseResult(sshStdOut);
                } else {
                    LOG.warn("ssh exited unsuccessfully. Return code: {}", p.exitValue());
                    return emptyList();
                }
            } catch (InterruptedException | IOException e) {
                LOG.warn("Unable to ssh into switch {}", iofSwitch.getId());
                return emptyList();
            }
        });

        // TODO fill in missing values (missing/disconnected switches)?
        Snapshot snapshot = new Snapshot();
        snapshot.setTimestamp(timestamp);
        snapshot.setNumRules(flows.entrySet().stream()
                .collect(toMap(
                        e -> e.getKey(),
                        e -> e.getValue().size())));
        snapshot.setClientMeasurements(flows.entrySet().stream()
                .collect(toMap(
                        e -> e.getKey(),
                        e -> extractClientMeasurements(e.getValue(), e.getKey()))));
        List<Measurement> serverMeasurements = flows.entrySet().stream()
                .map(e -> extractServerMeasurements(e.getValue(), e.getKey(), servers))
                .flatMap(List::stream)
                .collect(toMap(
                        msmt -> msmt.getPrefix(),
                        msmt -> msmt,
                        Measurement::add))
                .values().stream()
                .collect(toList());
        snapshot.setServerMeasurements(serverMeasurements);

        return snapshot;
    }

    private Map<IPv4Address, Double> calculateRatesAsMovingAverage(long intervalMillis) {
        List<IPv4Address> servers = config.getTopology().getServers();

        // Initialize rates to zero
        Map<IPv4Address, Double> rates = new HashMap<>();
        for (IPv4Address server : servers) {
            rates.put(server, 0D);
        }

        // Default return zeros
        if (snapshotHistory.isEmpty()) {
            return rates;
        }

        // Use newest snapshot as starting point
        Snapshot newestSnapshot = snapshotHistory.getFirst();
        long newestTimestamp = newestSnapshot.getTimestamp();

        // Use oldest snapshot (or first to exceed duration) as end point
        Snapshot oldestSnapshot = snapshotHistory.getLast();
        long oldestTimestamp = oldestSnapshot.getTimestamp();
        for (Snapshot snapshot : snapshotHistory) {
            if (newestTimestamp - snapshot.getTimestamp() > intervalMillis) {
                oldestSnapshot = snapshot;
                oldestTimestamp = snapshot.getTimestamp();
                break;
            }
        }

        // Calculate interval in seconds
        double actualIntervalSeconds = (newestTimestamp - oldestTimestamp) / 1000.0;

        // Add newest measurements
        for (Measurement measurement : newestSnapshot.getServerMeasurements()) {
            IPv4Address server = measurement.getPrefix().getValue();
            long newestBytes = measurement.getBytes();
            rates.put(server, (double) newestBytes);
        }

        // Subtract oldest measurements and divide by interval
        for (Measurement measurement : oldestSnapshot.getServerMeasurements()) {
            IPv4Address server = measurement.getPrefix().getValue();
            long oldestBytes = measurement.getBytes();
            double rate = (rates.get(server) - oldestBytes) / actualIntervalSeconds;
            rates.put(server, rate);
        }

        return rates;
    }

    private static List<Measurement> extractClientMeasurements(List<Flow> flows, DatapathId dpid) {
        short measurementTableId = MessageBuilder.getMeasurementTableId(dpid).getValue();
        Map<IPv4AddressWithMask, Measurement> measurementsByPrefix = flows.stream()
                .filter(flow -> flow.getTableId() == measurementTableId)
                .filter(flow -> flow.getCookie().getValue() != 0)
                .map(flow -> new Measurement(flow))
                .collect(toMap(
                        msmt -> msmt.getPrefix(),
                        msmt -> msmt,
                        Measurement::add));
        return new ArrayList<>(measurementsByPrefix.values());
    }

    private static List<Measurement> extractServerMeasurements(List<Flow> flows, DatapathId dpid, Set<IPv4Address> servers) {
        short forwardingTableId = MessageBuilder.getForwardingTableId(dpid).getValue();
        return flows.stream()
                .filter(flow -> flow.getTableId() == forwardingTableId)
                .filter(flow -> flow.getPrefix().getMask().asCidrMaskLength() == 32)
                .filter(flow -> servers.contains(flow.getPrefix().getValue()))
                .map(flow -> new Measurement(flow))
                .collect(toList());
    }

    private List<IOFSwitch> getActiveManagedSwitches() {
        return config.getTopology().getSwitches().stream()
                .map(dpid -> switchManager.getActiveSwitch(dpid))
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private List<DatapathId> getActiveManagedSwitchIds() {
        return config.getTopology().getSwitches().stream()
                .filter(dpid -> switchManager.getActiveSwitch(dpid) != null)
                .collect(toList());
    }

    private List<IOFSwitch> getActiveAccessSwitches() {
        return config.getTopology().getAccessSwitches().stream()
                .map(dpid -> switchManager.getActiveSwitch(dpid))
                .filter(Objects::nonNull)
                .collect(toList());
    }
}
