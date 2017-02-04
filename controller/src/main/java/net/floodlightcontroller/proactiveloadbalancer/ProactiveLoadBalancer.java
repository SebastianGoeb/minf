package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.proactiveloadbalancer.web.ProactiveLoadBalancerWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;

import static java.util.stream.Collectors.toSet;
import static net.floodlightcontroller.proactiveloadbalancer.Strategy.greedy;
import static net.floodlightcontroller.proactiveloadbalancer.Strategy.uniform;

public class ProactiveLoadBalancer implements IFloodlightModule, IOFMessageListener, IOFSwitchListener,
        IMeasurementListener, IProactiveLoadBalancerService {

    private static final Logger LOG = LoggerFactory.getLogger(ProactiveLoadBalancer.class);

    // Contants
    static final IPv4AddressWithMask CLIENT_RANGE = IPv4AddressWithMask.of("10.5.0.0/16");

    // Services
    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService switchManager;
    private ITrafficMeasurementService trafficMeasurementService;
    private IRestApiService restApiService;

    // Config
    private IPv4Address vip = null;
    private Strategy strategy = uniform;
    private Topology topology = new Topology();

    // Load balancing information
    private Map<DatapathId, Set<Flow>> flows = new HashMap<>();

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
        Collection<Class<? extends IFloodlightService>> moduleServices = new ArrayList<>();
        moduleServices.add(IProactiveLoadBalancerService.class);
        return moduleServices;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> serviceImpls = new HashMap<>();
        serviceImpls.put(IProactiveLoadBalancerService.class, this);
        return serviceImpls;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> moduleDependencies = new ArrayList<>();
        moduleDependencies.add(IFloodlightProviderService.class);
        moduleDependencies.add(IOFSwitchService.class);
        moduleDependencies.add(ITrafficMeasurementService.class);
        moduleDependencies.add(IRestApiService.class);
        return moduleDependencies;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchManager = context.getServiceImpl(IOFSwitchService.class);
        trafficMeasurementService = context.getServiceImpl(ITrafficMeasurementService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        // Services
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        switchManager.addOFSwitchListener(this);
        trafficMeasurementService.addMeasurementListener(this);
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
        if (flows.containsKey(dpid) && vip != null) {
            setupSwitch(dpid);
        }
    }

    @Override
    public void switchPortChanged(DatapathId dpid, OFPortDesc port, PortChangeType type) {}

    @Override
    public void switchChanged(DatapathId dpid) {}

    @Override
    public void switchDeactivated(DatapathId dpid) {}

    // ----------------------------------------------------------------
    // - IMeasurementListener methods
    // ----------------------------------------------------------------
    @Override
    public void newMeasurement() {
        if (vip != null && strategy == Strategy.greedy) {
            // Update flows
            buildFlows();

            // Update switches
            deleteAllLoadBalancingFlows();
            addAllLoadBalancingFlows();
        }
    }

    // ----------------------------------------------------------------
    // - IProactiveLoadBalancerService methods
    // ----------------------------------------------------------------
    @Override
    public void setVip(IPv4Address vip) {
        if (!Objects.equals(this.vip, vip)) {
            this.vip = vip;

            // Configure traffic measurement service
            trafficMeasurementService.setEnabled(strategy == greedy && vip != null);

            if (vip != null) {
                // Update flows
                buildFlows();

                // (Re)setup switches
                for (Bridge bridge : topology.getBridges()) {
                    DatapathId dpid = bridge.getDpid();
                    if (switchManager.getActiveSwitch(dpid) != null) {
                        setupSwitch(dpid);
                    }
                }
            } else {
                // TODO tear down switches
            }
        }
    }

    @Override
    public void setStrategy(Strategy strategy) {
        // Preconditions
        Objects.requireNonNull(strategy);

        if (!Objects.equals(this.strategy, strategy)) {
            this.strategy = strategy;

            // Configure traffic measurement service
            trafficMeasurementService.setEnabled(strategy == greedy && vip != null);

            if (vip != null) {
                // Update flows
                buildFlows();

                // (Re)setup switches
                for (Bridge bridge : topology.getBridges()) {
                    DatapathId dpid = bridge.getDpid();
                    if (switchManager.getActiveSwitch(dpid) != null) {
                        setupSwitch(dpid);
                    }
                }
            } else {
                // TODO tear down switches
            }
        }
    }

    @Override
    public void setTopology(Topology topology) {
        // Preconditions
        Objects.requireNonNull(strategy);

        if (!Objects.equals(this.topology, topology)) {
            this.topology = topology;

            // Configure traffic measurement service
            Set<DatapathId> dpids = topology.getBridges().stream().map(bridge -> bridge.getDpid()).collect(toSet());
            trafficMeasurementService.setDpids(dpids);

            if (vip != null) {
                // Update flows
                buildFlows();

                // (Re)setup switches
                for (Bridge bridge : topology.getBridges()) {
                    DatapathId dpid = bridge.getDpid();
                    if (switchManager.getActiveSwitch(dpid) != null) {
                        setupSwitch(dpid);
                    }
                }
            } else {
                // TODO tear down switches
            }
        }
    }

    private void setupSwitch(DatapathId dpid) {
        LOG.info("Setting up switch {}", dpid);
        IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
        OFFactory factory = ofSwitch.getOFFactory();

        // Add stub flows
        List<OFFlowMod> flowMods = MessageBuilder.addStubFlows(dpid, factory, vip);
        for (OFFlowMod flowMod : flowMods) {
            ofSwitch.write(flowMod);
        }

        // Add load balancing flows
        flowMods = MessageBuilder.addLoadBalancingFlows(dpid, factory, vip, flows.get(dpid));
        for (OFFlowMod flowMod : flowMods) {
            ofSwitch.write(flowMod);
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
                    PrefixTrie<Long> traffic = trafficMeasurementService.getMeasurement(dpid);
                    flows.put(dpid, FlowBuilder.buildFlowsGreedy(topology, flows.get(dpid), traffic));
                    break;
                default:
                    LOG.warn(MessageFormat.format("Unsupported strategy {0}", strategy));
                    throw new UnsupportedOperationException();
            }
        }
    }

    // Stub table
    private void addAllStubFlows() {
        for (Bridge bridge : topology.getBridges()) {
            DatapathId dpid = bridge.getDpid();
            IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
            if (ofSwitch != null) {
                OFFactory factory = ofSwitch.getOFFactory();
                List<OFFlowMod> flowMods = MessageBuilder.addStubFlows(dpid, factory, vip);
                for (OFFlowMod flowMod : flowMods) {
                    ofSwitch.write(flowMod);
                }
            }
        }
    }

    // LB table
    private void deleteAllLoadBalancingFlows() {
        for (Bridge bridge : topology.getBridges()) {
            DatapathId dpid = bridge.getDpid();
            IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
            if (ofSwitch != null) {
                OFFactory factory = ofSwitch.getOFFactory();
                List<OFFlowMod> flowMods = MessageBuilder.deleteLoadBalancingFlows(dpid, factory);
                for (OFFlowMod flowMod : flowMods) {
                    ofSwitch.write(flowMod);
                }
            }
        }
    }

    private void addAllLoadBalancingFlows() {
        for (Bridge bridge : topology.getBridges()) {
            DatapathId dpid = bridge.getDpid();
            IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
            if (ofSwitch != null) {
                OFFactory factory = ofSwitch.getOFFactory();
                List<OFFlowMod> flowMods = MessageBuilder.addLoadBalancingFlows(dpid, factory, vip, flows.get(dpid));
                for (OFFlowMod flowMod : flowMods) {
                    ofSwitch.write(flowMod);
                }
            }
        }
    }
}
