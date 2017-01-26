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
import org.projectfloodlight.openflow.types.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

public class ProactiveLoadBalancer
    implements IFloodlightModule, IOFMessageListener, IOFSwitchListener, IProactiveLoadBalancerService {

    private static final short NUM_TABLES = 5;
    private static final int LOAD_BALANCING_TABLE_ID_OFFSET = 2;

    // Contants
    public static final IPv4AddressWithMask SRC_RANGE = IPv4AddressWithMask.of("10.5.0.0/16");

    // Utility fields
    private static Logger log = LoggerFactory.getLogger(ProactiveLoadBalancer.class);

    // Services
    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService switchManager;
    private IRestApiService restApiService;

    // Load balancing information
    private Set<DatapathId> dpids;
    private Map<IPv4Address, AddressPool> addressPools;
    private Map<IPv4Address, Set<Rule>> ruleSets;

    // TODO move?
    public static TableId getBaseTableId(DatapathId dpid) {
        return TableId.of(((short) dpid.getLong() - 1) * NUM_TABLES + 1);
    }

    public static TableId getLoadBalancingTableId(DatapathId dpid) {
        return TableId.of(ProactiveLoadBalancer.getBaseTableId(dpid).getValue() + LOAD_BALANCING_TABLE_ID_OFFSET);
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
                log.info("Packet in");
                break;
            case FLOW_REMOVED:
                log.info("Flow removed");
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
        // moduleDependencies.add(IThreadPoolService.class);
        moduleDependencies.add(IRestApiService.class);
        return moduleDependencies;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchManager = context.getServiceImpl(IOFSwitchService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        // Network
        dpids = new HashSet<>();

        // Load balancing
        addressPools = new HashMap<>();
        ruleSets = new HashMap<>();

        // Services
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        switchManager.addOFSwitchListener(this);
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
        // Install rules
        log.info("Installing rules on switch {}", switchId);

		for (Entry<IPv4Address, Set<Rule>> entry : ruleSets.entrySet()) {
			installRulesOnSwitch(switchManager.getActiveSwitch(switchId), entry.getKey(), entry.getValue());
		}
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        log.info("Switch port changed {}", switchId);
    }

    @Override
    public void switchChanged(DatapathId switchId) {
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
    }

    // ----------------------------------------------------------------
    // - IProactiveLoadBalancerService methods
    // ----------------------------------------------------------------
    @Override
    public void addSwitch(DatapathId dpid) {
        // If already present, ignore
        if (dpids.contains(dpid)) {
            log.info("Switch {} already registered. Ignoring request to add.", dpid);
            return;
        }

        // Add switch
        log.info("Adding switch {}.", dpid);
        dpids.add(dpid);

        // Install rules
        log.info("Installing rules on switch {}", dpid);
        for (Entry<IPv4Address, Set<Rule>> entry : ruleSets.entrySet()) {
            installRulesOnSwitch(switchManager.getActiveSwitch(dpid), entry.getKey(), entry.getValue());
        }
    }

    @Override
    public DatapathId deleteSwitch(DatapathId dpid) {
        // If not present, ignore
        if (!dpids.contains(dpid)) {
            log.info("Switch {} not registered. Ignoring request to delete.", dpid);
            return null;
        }

        // Delete switch
        log.info("Deleted switch {}", dpid);
        dpids.remove(dpid);

        // Uninstall rules from switch
        log.info("Uninstalling rules from switch {}", dpid);
        for (Entry<IPv4Address, Set<Rule>> entry : ruleSets.entrySet()) {
            uninstallRulesFromSwitch(switchManager.getActiveSwitch(dpid), entry.getKey(), entry.getValue());
        }
        return dpid;
    }

    @Override
    public void addAddressPool(IPv4Address vip, AddressPool addressPool) {
        if (addressPools.containsKey(vip) && Objects.equals(addressPools.get(vip), addressPool)) {
            log.info("Identical address pool for vip {} already present. Ignoring request to add.", vip);
            return;
        }

        // Add (or update) address pool
        log.info("Adding address pool for vip {}.", vip);
        addressPools.put(vip, addressPool);

        // Update rules
        log.info("Updating rules for vip {}.", vip);
        Set<Rule> previousRules = ruleSets.get(vip);
        ruleSets.put(vip, RuleBuilder.buildRules(addressPool));

        // Uninstall previous rules
        log.info("Uninstalling previous rules for vip {} from all switches.", vip);
        for (DatapathId dpid : dpids) {
            uninstallRulesFromSwitch(switchManager.getActiveSwitch(dpid), vip, previousRules);
        }

        // Install rules
        log.info("Installing rules for vip {} on all switches.", vip);
        for (DatapathId dpid : dpids) {
            installRulesOnSwitch(switchManager.getActiveSwitch(dpid), vip, ruleSets.get(vip));
        }
    }

    @Override
    public AddressPool deleteAddressPool(IPv4Address vip) {
        if (!addressPools.containsKey(vip)) {
            log.info("Address pool for vip {} not present. Ignoring request to delete.", vip);
            return null;
        }

        // Delete address pool
        log.info("Deleting address pool for vip {}.", vip);
        AddressPool previousAddressPool = addressPools.remove(vip);

        // Delete rules
        log.info("Deleting rules for vip {}.", vip);
        Set<Rule> previousRules = ruleSets.remove(vip);

        // Uninstall previous rules
        log.info("Uninstalling previous rules for vip {} from all switches.", vip);
        for (DatapathId dpid : dpids) {
            uninstallRulesFromSwitch(switchManager.getActiveSwitch(dpid), vip, previousRules);
        }

        return previousAddressPool;
    }

    private static void installRulesOnSwitch(IOFSwitch ofSwitch, IPv4Address vip, Set<Rule> rules) {
        if (ofSwitch == null || vip == null || rules == null) {
            return;
        }

        DatapathId dpid = ofSwitch.getId();

        for (Rule rule : rules) {
            IPv4AddressWithMask prefix = rule.getPrefix();
            IPv4Address dip = rule.getDip();
            FlowBuilder.addIncomingLoadBalancing(ofSwitch, getLoadBalancingTableId(dpid), prefix, vip, dip);
        }
    }

    private static void uninstallRulesFromSwitch(IOFSwitch ofSwitch, IPv4Address vip, Set<Rule> rules) {
        if (ofSwitch == null || vip == null || rules == null) {
            return;
        }

        DatapathId dpid = ofSwitch.getId();

        for (Rule rule : rules) {
            IPv4AddressWithMask prefix = rule.getPrefix();
            IPv4Address dip = rule.getDip();
            FlowBuilder.deleteStrictIncomingLoadBalancing(ofSwitch, getLoadBalancingTableId(dpid), prefix, vip, dip);
        }
    }
}
