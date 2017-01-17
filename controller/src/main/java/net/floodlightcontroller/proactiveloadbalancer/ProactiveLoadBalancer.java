package net.floodlightcontroller.proactiveloadbalancer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Ordering;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.proactiveloadbalancer.util.FlowBuilder;
import net.floodlightcontroller.proactiveloadbalancer.web.ProactiveLoadBalancerWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class ProactiveLoadBalancer
		implements IFloodlightModule, IOFMessageListener, IOFSwitchListener, IProactiveLoadBalancerService {

	// Utility fields
	protected static Logger log = LoggerFactory.getLogger(ProactiveLoadBalancer.class);

	// Services
	protected IFloodlightProviderService floodlightProvider;
	protected IThreadPoolService threadPoolService;
	protected IOFSwitchService switchManager;
	protected IRestApiService restApiService;

	// Network information
	private Set<DatapathId> usedSwitches;

	// Load balancing information
	private Map<IPv4Address, AddressPool> addressPools;
	private Map<IPv4Address, Map<IPv4AddressWithMask, IPv4Address>> prefixMaps;
	
	// Contants
	private static final IPv4AddressWithMask SRC_RANGE = IPv4AddressWithMask.of("10.5.0.0/16");
	private static final int INCOMING_PRIORITY = 100;
	
	
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
		moduleServices.add(IServerLoadBalancerService.class);
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
		moduleDependencies.add(IThreadPoolService.class);
		moduleDependencies.add(IRestApiService.class);
		return moduleDependencies;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		switchManager = context.getServiceImpl(IOFSwitchService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// Network
		usedSwitches = new HashSet<>();
		
		// Load balancing
		addressPools = new HashMap<>();
		prefixMaps = new HashMap<>();

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
		log.info("Switch added " + switchId);
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		log.info("Switch disconnected " + switchId);
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		log.info("Switch activated " + switchId);
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
		log.info("Switch port changed " + switchId);
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		log.info("Switch changed " + switchId);
	}

	@Override
	public void switchDeactivated(DatapathId switchId) {
		log.info("Switch deactivated " + switchId);
	}

	// ----------------------------------------------------------------
	// - IProactiveLoadBalancerService methods
	// ----------------------------------------------------------------
	@Override
	public void addSwitch(DatapathId dpid) {
		log.info("Adding switch with dpid " + dpid);
		if (usedSwitches.contains(dpid)) {
			// TODO do cleanup?
		}
		usedSwitches.add(dpid);
		if (switchManager.getActiveSwitch(dpid) != null) {
			// push flows to switch
		}
		// TODO activate after add
	}

	@Override
	public DatapathId deleteSwitch(DatapathId dpid) {
		log.info("Deleting switch with dpid " + dpid);
		if (switchManager.getActiveSwitch(dpid) != null) {
			// TODO remove flows from switch
		}
		return usedSwitches.remove(dpid) ? dpid : null;
	}

	@Override
	public void addAddressPool(IPv4Address vip, AddressPool addressPool) {
		log.info("Adding address pool for vip " + vip);
		AddressPool previousAddressPool = addressPools.get(vip);
		if (previousAddressPool != null && previousAddressPool.equals(addressPool)) {
			return;
		}
		addressPools.put(vip, addressPool);
		
		// Schedule update to flows
		try {
			threadPoolService.getScheduledExecutor().submit(() -> updateFlows(vip)).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

	@Override
	public AddressPool deleteAddressPool(IPv4Address vip) {
		log.info("Deleting address pool for vip " + vip);
		deleteFlows(vip);
		prefixMaps.remove(vip);
		return addressPools.remove(vip);
	}
	
	private void updateFlows(IPv4Address vip) {
		// Sort dips
		List<IPv4Address> dips = Ordering.natural().immutableSortedCopy(addressPools.get(vip).getDips());
		log.info("Sorted dips: " + dips);
		
		// scale weights to int values summing to next power of 2 after dips.size()
		int bits = dips.size() == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(dips.size() - 1);
		List<Integer> scaledWeights = scaleWeights(Collections.nCopies(dips.size(), 1d), 1 << bits);
		Map<IPv4AddressWithMask, IPv4Address> map = makerules(dips, scaledWeights, bits);
		prefixMaps.put(vip, map);
		log.info("Prefix maps: " + map);
		
		// Schedule pushing flows to switches
		try {
			threadPoolService.getScheduledExecutor().submit(() -> addFlows(vip)).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}
	
	private void addFlows(IPv4Address vip) {
		log.info("Adding flows for vip " + vip);
		for (DatapathId dpid : usedSwitches) {
			IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
			OFFactory factory = ofSwitch != null ? ofSwitch.getOFFactory() : OFFactories.getFactory(OFVersion.OF_13);
			
			// Build OpenFlow flowmods
			for (Entry<IPv4AddressWithMask, IPv4Address> prefixMap : prefixMaps.get(vip).entrySet()) {
				IPv4AddressWithMask prefix = prefixMap.getKey();
				IPv4Address dip = prefixMap.getValue();
				
				Match match = FlowBuilder.buildIncomingMatch(factory, vip, prefix);
				List<OFAction> actions = FlowBuilder.buildIncomingActionList(factory, dip);
				List<OFInstruction> instructions = FlowBuilder.buildIncomingInstructionList(factory, actions, dpid);
				
				OFFlowAdd.Builder builder = factory.buildFlowAdd()
						.setTableId(TableId.of(0))
						.setMatch(match)
						.setPriority(INCOMING_PRIORITY)
						.setInstructions(instructions);
				// Transition timeouts?

				log.info("Rule: " + prefixMap);
				log.info("Match: " + match);
				log.info("Instructions: " + instructions);
				if (ofSwitch != null) {
					ofSwitch.write(builder.build());
				}
			}
		}
	}
	
	private void deleteFlows(IPv4Address vip) {
		log.info("Deleting flows for vip " + vip);
		for (DatapathId dpid : usedSwitches) {
			IOFSwitch ofSwitch = switchManager.getActiveSwitch(dpid);
			if (ofSwitch == null) {
				continue;
			}
			
			OFFactory factory = ofSwitch.getOFFactory();
			
			// Build OpenFlow flowmods
			for (Entry<IPv4AddressWithMask, IPv4Address> addressMaps : prefixMaps.get(vip).entrySet()) {
				IPv4AddressWithMask prefix = addressMaps.getKey();
				IPv4Address dip = addressMaps.getValue();
				
				Match match = FlowBuilder.buildIncomingMatch(factory, vip, prefix);
				List<OFAction> actions = FlowBuilder.buildIncomingActionList(factory, dip);
				List<OFInstruction> instructions = FlowBuilder.buildIncomingInstructionList(factory, actions, dpid);
				
				OFFlowDeleteStrict.Builder builder = factory.buildFlowDeleteStrict()
						.setTableId(TableId.of(0))
						.setMatch(match)
						.setPriority(INCOMING_PRIORITY)
						.setInstructions(instructions);
				// Transition timeouts?
				ofSwitch.write(builder.build());
			}
		}
	}
	
	// Compile weighted dips to prefix map
	private List<Integer> scaleWeights(List<Double> weights, double total) {
        // Cumulative sum
        double[] cumul = new double[weights.size()];
        double sum = 0;
        for (int i = 0; i < cumul.length; i++) {
        	sum += weights.get(i);
        	cumul[i] = sum;
        }

        // Scale and round
        int[] scaled = new int[cumul.length];
        for (int i = 0; i < cumul.length; i++) {
        	scaled[i] = (int) Math.round(cumul[i] * total / sum);
        }

        // Un-cumulative
        List<Integer> normalizedWeights = new ArrayList<>(scaled.length);
        normalizedWeights.add(scaled[0]);
        for (int i = 1; i < scaled.length; i++) {
        	normalizedWeights.add(scaled[i] - scaled[i-1]);
        }
        return normalizedWeights;
	}
	
	private Map<IPv4AddressWithMask, IPv4Address> makerules(List<IPv4Address> dips, List<Integer> weights, int bits) {
		// Split weights into powers of 2
		List<IPv4Address> splitDips = new ArrayList<>();
		List<Integer> splitWeights = new ArrayList<>();
		for (int i = 0; i < weights.size(); i++) {
			int weight = weights.get(i);
			for (int j = 1; j <= weight; j <<= 1) {
				if ((j & weight) != 0) {
					splitWeights.add(j);
					splitDips.add(dips.get(i));
				}
			}
		}

		// Turn into prefixes
		LinkedHashMap<IPv4AddressWithMask, IPv4Address> map = new LinkedHashMap<>();
		int valueIncrement = Integer.MIN_VALUE >>> (SRC_RANGE.getMask().asCidrMaskLength() + bits - 1);
		int value = SRC_RANGE.getValue().getInt();
		int mask = IPv4Address.ofCidrMaskLength((SRC_RANGE.getMask().asCidrMaskLength() + bits)).getInt();
		for (int i = 0; i < splitWeights.size(); i++) {
			int weight = splitWeights.get(i);
			IPv4AddressWithMask prefix = IPv4Address.of(value).withMask(IPv4Address.of(mask * weight));
			map.put(prefix, splitDips.get(i));
			value += valueIncrement * weight;
		}

		return map;
	}
}
