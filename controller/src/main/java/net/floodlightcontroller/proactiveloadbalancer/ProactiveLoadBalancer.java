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
import net.floodlightcontroller.proactiveloadbalancer.domain.*;
import net.floodlightcontroller.proactiveloadbalancer.util.IPUtil;
import net.floodlightcontroller.proactiveloadbalancer.util.IPv4AddressRange;
import net.floodlightcontroller.proactiveloadbalancer.util.PrefixTrie;
import net.floodlightcontroller.proactiveloadbalancer.web.ProactiveLoadBalancerWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.*;
import static net.floodlightcontroller.proactiveloadbalancer.domain.Strategy.non_uniform;
import static net.floodlightcontroller.proactiveloadbalancer.domain.Strategy.traditional;

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
    private Map<DatapathId, IPv4Address> vips = new HashMap<>();

    // Scheduling
    private ScheduledFuture<?> collectionFuture;

    // Runtime stuff
    private Map<DatapathId, List<Measurement>> measurements;
    private Map<Strategy, List<LoadBalancingFlow>> logicalLoadBalancingFlows;
    private Map<Strategy, Map<DatapathId, List<LoadBalancingFlow>>> physicalLoadBalancingFlows;

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
                    if (config != null && config.getStrategyRanges().containsKey(traditional)) {
                        IPv4AddressRange range = config.getStrategyRanges().get(traditional);
                        if (range.contains(src)) {
                            // If is already being load balanced
                            if (logicalLoadBalancingFlows.get(traditional).stream().noneMatch(flow -> flow.getPrefix().getValue().equals(src))) {
                                logicalLoadBalancingFlows.get(traditional).add(new LoadBalancingFlow(src.withMaskOfLength(32), null));
                                updateLoadBalancing(singletonList(traditional));
                                writeLoadBalancingFlows(getActiveManagedSwitches(), singletonList(traditional));
                            }
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
            List<IOFSwitch> switchAsList = singletonList(switchManager.getActiveSwitch(dpid));
            writePermanentFlows(switchAsList);
            writeMeasurementFlows(switchAsList);
            writeLoadBalancingFlows(switchAsList, config.getStrategyRanges().keySet());
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

                // Delete all flows
                MessageBuilder.deleteAllFlows(dpid, factory).forEach(iofSwitch::write);
            });
        }
    }

    private void setup() {
        if (config != null) {
            LOG.info("Setting up switches " + Joiner.on(", ").join(getActiveManagedSwitches().stream()
                    .map(IOFSwitch::getId).map(DatapathId::getLong).collect(toList())));
            List<DatapathId> switches = config.getTopology().getSwitches();

            // Initialize vips
            // TODO assign vips more intelligently?
            int vipBase = config.getdVipRange().getValue().getInt();
            for (DatapathId dpid : switches) {
                vips.put(dpid, IPv4Address.of(vipBase + (int) dpid.getLong()));
            }

            // Initialize permanent flows
            writePermanentFlows(getActiveManagedSwitches());

            // Initialize measurements
            measurements = switches.stream().collect(toMap(dpid -> dpid, dpid -> emptyList()));
            writeMeasurementFlows(getActiveManagedSwitches());

            // Initialize load balancing flows
            logicalLoadBalancingFlows = new HashMap<>();
            physicalLoadBalancingFlows = new HashMap<>();
            updateLoadBalancing(config.getStrategyRanges().keySet());
            writeLoadBalancingFlows(getActiveManagedSwitches(), config.getStrategyRanges().keySet());

            // Start measurement cycle
            collectionFuture = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(() -> {
                updateMeasurements();
                writeMeasurementFlows(getActiveManagedSwitches());
                if (config.getStrategyRanges().keySet().contains(non_uniform)) {
                    updateLoadBalancing(singletonList(non_uniform));
                    writeLoadBalancingFlows(getActiveManagedSwitches(), singletonList(non_uniform));
                }
            }, config.getMeasurementInterval(), config.getMeasurementInterval(), TimeUnit.SECONDS);
            // TODO run a shorter measurement interval the first time?
        }
    }

    private void updateMeasurements() {
        if (config.getMeasurementCommands() == null) {
            measurements = getMeasurements(getActiveManagedSwitches());
        } else {
            measurements = getMeasurementsFromSsh(getActiveManagedSwitches());
        }
    }

    private void updateLoadBalancing(Iterable<Strategy> strategies) {
        for (Strategy strategy : strategies) {
            // Build logical flows
            List<LoadBalancingFlow> logicalFlows = buildLogicalFlows(strategy);
            logicalLoadBalancingFlows.put(strategy, logicalFlows);

            // Build physical flows
            physicalLoadBalancingFlows.put(strategy, FlowBuilder.buildPhysicalFlows(config, logicalFlows, vips));
        }
    }

    private void writeMeasurementFlows(Iterable<IOFSwitch> switches) {
        // TODO parallelize?
        for (IOFSwitch iofSwitch : switches) {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            List<IPv4AddressWithMask> flows = FlowBuilder.buildMeasurementFlows(measurements.get(dpid), config);

            MessageBuilder.deleteMeasurementFlows(dpid, factory).forEach(iofSwitch::write);
            MessageBuilder.addMeasurementFlows(dpid, factory, vips.get(dpid), flows).forEach(iofSwitch::write);
        }
    }

    private void writeLoadBalancingFlows(Iterable<IOFSwitch> switches, Iterable<Strategy> strategies) {
        // TODO parallelize?
        for (IOFSwitch iofSwitch : switches) {
            DatapathId dpid = iofSwitch.getId();
            OFFactory factory = iofSwitch.getOFFactory();
            IPv4Address vip = vips.get(dpid);

            for (Strategy strategy : strategies) {
                U64 cookie = strategy.cookie();
                List<LoadBalancingFlow> flows = physicalLoadBalancingFlows.get(strategy).get(dpid);

                if (strategy != traditional) {
                    MessageBuilder.deleteLoadBalancingFlows(dpid, factory, cookie).forEach(iofSwitch::write);
                }
                // TODO for traditional only write new flows
                MessageBuilder.addLoadBalancingIngressFlows(dpid, factory, vip, flows, cookie, strategy == traditional).forEach(iofSwitch::write);
            }
        }
    }

    private void writePermanentFlows(Iterable<IOFSwitch> switches) {
        // TODO parallelize?
        for (IOFSwitch iofSwitch : switches) {
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
            if (config.getStrategyRanges().containsKey(traditional)) {
                IPv4AddressRange range = config.getStrategyRanges().get(traditional);
                // TODO subsets for hierarchical load balancing?
                List<IPv4AddressWithMask> prefixes = IPUtil.nonOverlappingPrefixes(range);
                MessageBuilder.addLoadBalancingControllerFlows(dpid, factory, vip, prefixes).forEach(iofSwitch::write);
            }

            // Add forwarding flows
            List<ForwardingFlow> physicalFordwardingFlows = FlowBuilder.buildForwardingFlows(dpid, config, vips);
            MessageBuilder.addForwardingFlows(dpid, factory, physicalFordwardingFlows).forEach(iofSwitch::write);
        }
    }

    private List<LoadBalancingFlow> buildLogicalFlows(Strategy strategy) {
        // Merge measurements into single tree
        // TODO refactor into field
        List<Measurement> allMeasurements = measurements.values().stream()
                .flatMap(Collection::stream)
                .collect(toList());
        PrefixTrie<Double> mergedMeasurements = FlowBuilder.mergeMeasurements(allMeasurements, config);

        // Previous logical flows
        List<LoadBalancingFlow> logicalFlows = logicalLoadBalancingFlows.get(strategy);

        // Build logical flows
        switch (strategy) {
            case uniform:
                return FlowBuilder.buildFlowsUniform(config, logicalFlows);

            case non_uniform:
                return FlowBuilder.buildFlowsNonUniform(config, logicalFlows, mergedMeasurements);

            case traditional:
                // FIXME this requires server msmts, not client msmts
                return FlowBuilder.buildFlowsClassic(config, logicalFlows, mergedMeasurements);

            default:
                throw new UnsupportedOperationException("Strategy " + strategy + " not supported");
        }
    }

    private static Map<DatapathId, List<Measurement>> getMeasurements(Iterable<IOFSwitch> switches) {
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
                    if (match.isExact(MatchField.IPV4_SRC)) {
                        prefix = match.get(MatchField.IPV4_SRC).withMaskOfLength(32);
                    } else if (match.isPartiallyMasked(MatchField.IPV4_SRC)) {
                        Masked<IPv4Address> masked = match.getMasked(MatchField.IPV4_SRC);
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

    private Map<DatapathId, List<Measurement>> getMeasurementsFromSsh(Iterable<IOFSwitch> switches) {
        Objects.requireNonNull(switches);

        // TODO parallelize?
        Map<DatapathId, List<Measurement>> newMeasurements = new HashMap<>();
        for (IOFSwitch iofSwitch : switches) {
            DatapathId dpid = iofSwitch.getId();

            MeasurementCommand command = config.getMeasurementCommands().get(dpid);
            try {
                Process p = new ProcessBuilder("ssh", command.getEndpoint(), command.getCommand()).redirectError(new File("/dev/null")).start();
                p.waitFor();
                if (p.exitValue() != 0) {
                    LOG.warn("ssh exited unsuccessfully. Return code: {}", p.exitValue());
                } else {
                    String result = CharStreams.toString(new InputStreamReader(p.getInputStream()));
                    List<Measurement> parsedMeasurements = SshParser.parseResult(result, MessageBuilder.getMeasurementTableId(dpid).getValue());
                    newMeasurements.put(dpid, parsedMeasurements);
                }
            } catch (InterruptedException | IOException e) {
                LOG.warn("Unable to ssh into switch {}", iofSwitch.getId());
            }
        }
        return newMeasurements;
    }

    private List<IOFSwitch> getActiveManagedSwitches() {
        return config.getTopology().getSwitches().stream()
                .map(dpid -> switchManager.getActiveSwitch(dpid))
                .filter(ofSwitch -> ofSwitch != null)
                .collect(toList());
    }
}
