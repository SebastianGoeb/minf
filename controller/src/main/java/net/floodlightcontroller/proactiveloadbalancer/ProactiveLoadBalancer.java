package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.proactiveloadbalancer.web.ProactiveLoadBalancerWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;

import static java.util.stream.Collectors.toSet;
import static net.floodlightcontroller.proactiveloadbalancer.Strategy.uniform;

public class ProactiveLoadBalancer
    implements IFloodlightModule, IOFMessageListener, IOFSwitchListener, IMeasurementListener, IProactiveLoadBalancerService {

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

    // Load balancing information (simplified)
    private Set<Flow> flows = new HashSet<>();

    // Load balancing information
    @Deprecated
    private Set<DatapathId> dpids;
    @Deprecated
    private Map<IPv4Address, AddressPool> addressPools;
    @Deprecated
    private Map<IPv4Address, Set<Flow>> flowSets;

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
        // moduleDependencies.add(IThreadPoolService.class);
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
        // Network
        dpids = new HashSet<>();

        // Load balancing
        addressPools = new HashMap<>();
        flowSets = new HashMap<>();

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
    public void switchAdded(DatapathId switchId) {
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        // Install flows
        LOG.info("Installing flows on switch {}", switchId);

        for (Entry<IPv4Address, Set<Flow>> entry : flowSets.entrySet()) {
            installFlowsOnSwitch(switchManager.getActiveSwitch(switchId), entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
    }

    @Override
    public void switchChanged(DatapathId switchId) {
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
    }

    // ----------------------------------------------------------------
    // - IMeasurementListener methods
    // ----------------------------------------------------------------
    @Override
    public void newMeasurement() {
        // TODO
    }

    // ----------------------------------------------------------------
    // - IProactiveLoadBalancerService methods
    // ----------------------------------------------------------------
    @Override
    public void setVip(IPv4Address vip) {
        this.vip = vip;

        // TODO queue update?
        if (vip != null) {
            for (Bridge bridge : topology.getBridges()) {
                buildFlows(bridge.getDpid());
            }
        }
    }

    @Override
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;

        // TODO queue update?
        if (vip != null) {
            for (Bridge bridge : topology.getBridges()) {
                buildFlows(bridge.getDpid());
            }
        }
    }

    @Override
    public void setTopology(Topology topology) {
        this.topology = topology;
        Set<DatapathId> dpids = topology.getBridges().stream().map(bridge -> bridge.getDpid()).collect(toSet());
        trafficMeasurementService.setDpids(dpids);

        // TODO queue update?
        if (vip != null) {
            for (Bridge bridge : topology.getBridges()) {
                buildFlows(bridge.getDpid());
            }
        }
    }

    @Override
    @Deprecated
    public void addSwitch(DatapathId dpid) {
        // If already present, ignore
        if (dpids.contains(dpid)) {
            LOG.info("Switch {} already registered. Ignoring request to add.", dpid);
            return;
        }

        // Add switch
        LOG.info("Adding switch {}.", dpid);
        dpids.add(dpid);

        // Install flows
        LOG.info("Installing flows on switch {}", dpid);
        for (Entry<IPv4Address, Set<Flow>> entry : flowSets.entrySet()) {
            installFlowsOnSwitch(switchManager.getActiveSwitch(dpid), entry.getKey(), entry.getValue());
        }
    }

    @Override
    @Deprecated
    public DatapathId deleteSwitch(DatapathId dpid) {
        // If not present, ignore
        if (!dpids.contains(dpid)) {
            LOG.info("Switch {} not registered. Ignoring request to delete.", dpid);
            return null;
        }

        // Delete switch
        LOG.info("Deleted switch {}", dpid);
        dpids.remove(dpid);

        // Uninstall flows from switch
        LOG.info("Uninstalling flows from switch {}", dpid);
        for (Entry<IPv4Address, Set<Flow>> entry : flowSets.entrySet()) {
            uninstallFlowsFromSwitch(switchManager.getActiveSwitch(dpid), entry.getKey(), entry.getValue());
        }
        return dpid;
    }

    @Override
    @Deprecated
    public void addAddressPool(IPv4Address vip, AddressPool addressPool) {
        if (addressPools.containsKey(vip) && Objects.equals(addressPools.get(vip), addressPool)) {
            LOG.info("Identical address pool for vip {} already present. Ignoring request to add.", vip);
            return;
        }

        // Add (or update) address pool
        LOG.info("Adding address pool for vip {}.", vip);
        addressPools.put(vip, addressPool);

        // Update flows
        LOG.info("Updating flows for vip {}.", vip);
        Set<Flow> previousFlows = flowSets.get(vip);
        flowSets.put(vip, FlowBuilder.buildFlows(addressPool));

        // Uninstall previous flows
        LOG.info("Uninstalling previous flows for vip {} from all switches.", vip);
        for (DatapathId dpid : dpids) {
            uninstallFlowsFromSwitch(switchManager.getActiveSwitch(dpid), vip, previousFlows);
        }

        // Install flows
        LOG.info("Installing flows for vip {} on all switches.", vip);
        for (DatapathId dpid : dpids) {
            installFlowsOnSwitch(switchManager.getActiveSwitch(dpid), vip, flowSets.get(vip));
        }
    }

    @Override
    @Deprecated
    public AddressPool deleteAddressPool(IPv4Address vip) {
        if (!addressPools.containsKey(vip)) {
            LOG.info("Address pool for vip {} not present. Ignoring request to delete.", vip);
            return null;
        }

        // Delete address pool
        LOG.info("Deleting address pool for vip {}.", vip);
        AddressPool previousAddressPool = addressPools.remove(vip);

        // Delete flows
        LOG.info("Deleting flows for vip {}.", vip);
        Set<Flow> previousFlows = flowSets.remove(vip);

        // Uninstall previous flows
        LOG.info("Uninstalling previous flows for vip {} from all switches.", vip);
        for (DatapathId dpid : dpids) {
            uninstallFlowsFromSwitch(switchManager.getActiveSwitch(dpid), vip, previousFlows);
        }

        return previousAddressPool;
    }

    private Set<Flow> buildFlows(DatapathId dpid) {
        switch (strategy) {
            case uniform:
                return FlowBuilder.buildFlowsUniform(topology, flows);
            case greedy:
                // TODO
                LOG.warn(MessageFormat.format("Unsupported strategy {0}", strategy));
                return FlowBuilder.buildFlowsGreedy(topology, flows, trafficMeasurementService.getMeasurement(dpid));
            default:
                LOG.warn(MessageFormat.format("Unsupported strategy {0}", strategy));
                throw new UnsupportedOperationException();
        }
    }

    private static void installFlowsOnSwitch(IOFSwitch ofSwitch, IPv4Address vip, Set<Flow> flows) {
        if (ofSwitch == null || vip == null || flows == null) {
            return;
        }

        DatapathId dpid = ofSwitch.getId();

        for (Flow flow : flows) {
            IPv4AddressWithMask prefix = flow.getPrefix();
            IPv4Address dip = flow.getDip();
            ofSwitch.write(MessageBuilder.addIncomingLoadBalancing(ofSwitch.getOFFactory(), MessageBuilder.getLoadBalancingTableId(dpid), prefix, vip, dip));
        }
    }

    private static void uninstallFlowsFromSwitch(IOFSwitch ofSwitch, IPv4Address vip, Set<Flow> flows) {
        if (ofSwitch == null || vip == null || flows == null) {
            return;
        }

        DatapathId dpid = ofSwitch.getId();

        for (Flow flow : flows) {
            IPv4AddressWithMask prefix = flow.getPrefix();
            IPv4Address dip = flow.getDip();
            ofSwitch.write(MessageBuilder.deleteStrictIncomingLoadBalancing(ofSwitch.getOFFactory(), MessageBuilder.getLoadBalancingTableId(dpid), prefix, vip, dip));
        }
    }
}
