package net.floodlightcontroller.serverloadbalancer;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.serverloadbalancer.web.ServerLoadBalancerWebRoutable;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;

public class ServerLoadBalancer implements IFloodlightModule, IOFMessageListener, IOFSwitchListener, IServerLoadBalancerService {

    private static final short TCP_SYN = 0x2;

    private static final int MICROFLOW_PRIORITY = 800;
    private static final int TRANSITION_PRIORITY = 700;
    private static final int IN_PRIORITY = 600;
    private static final int OUT_PRIORITY = 500;

    protected static Logger logger;
    protected IFloodlightProviderService floodlightProvider;
    protected IThreadPoolService threadPoolService;
    protected IOFSwitchService switchManager;
    protected IRestApiService restApiService;
    private List<DatapathId> dpids;
    private Map<DatapathId, List<Transition>> transitions;
    private ScheduledFuture<?> loadStatsFuture;
    private Map<Integer, Server> servers;

    // Configuration
    private Config config;

    // State
    private AssignmentTree assignmentTree;

    @Override
    public String getName() {
        return ServerLoadBalancer.class.getSimpleName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    // Module methods
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<>();
        l.add(IServerLoadBalancerService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
                IFloodlightService> m =
                new HashMap<>();
        m.put(IServerLoadBalancerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> list = new ArrayList<>();
        list.add(IFloodlightProviderService.class);
        list.add(IThreadPoolService.class);
        list.add(IRestApiService.class);
        return list;
    }

    // Init/Startup methods
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        logger = LoggerFactory.getLogger(ServerLoadBalancer.class);
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
        switchManager = context.getServiceImpl(IOFSwitchService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        // Init configuration
        // TODO move?
        config = new Config()
                .setMaxPrefixLength(3)
                .setCoreSwitch(new SwitchDesc())
                .setLoadStatsInterval(1);

        // Floodlight Provice Service
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);

        // Switch Service
        switchManager.addOFSwitchListener(this);

        // Thread Pool Service
//        loadStatsFuture = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(
//                new LoadStatsCollector(),
//                config.getLoadStatsInterval(),
//                config.getLoadStatsInterval(),
//                TimeUnit.SECONDS);

        // Init state
        assignmentTree = ServerLoadBalancerUtil.generateAssignmentTreeOptimal(config);
        dpids = new ArrayList<>();
        transitions = new HashMap<>();
        servers = new HashMap<>();

        // REST Service
        restApiService.addRestletRoutable(new ServerLoadBalancerWebRoutable());
    }

    // Process incoming packets
    @Override
    public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        DatapathId dpid = sw.getId();
        switch (msg.getType()) {
            case PACKET_IN:
                Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
                if (EthType.IPv4.getValue() == eth.getEtherType()) {
                    IPv4 ipv4 = (IPv4) eth.getPayload();

                    if (ipv4.getProtocol().equals(IpProtocol.TCP)) {
                        // Handle TCP packets for transitioning IP prefixes
                        TCP tcp = (TCP) ipv4.getPayload();
                        IPv4Address src = ipv4.getSourceAddress();

                        // Find transition where any old assignment matches source IP
                        Transition matchingTransition = transitions.get(dpid).stream()
                                .filter(t -> t.getFrom().stream().anyMatch(a -> a.getPrefix().contains(src)))
                                .findFirst()
                                .get();

                        // Find new assignment matching source IP
                        Assignment toAssignment = matchingTransition.getTo().stream()
                                .filter(a -> a.getPrefix().contains(src))
                                .findFirst()
                                .get();

                        // Find old assignment matching source IP
                        Assignment fromAssignment = matchingTransition.getFrom().stream()
                                .filter(a -> a.getPrefix().contains(src))
                                .findFirst()
                                .get();

                        if ((tcp.getFlags() & TCP_SYN) != 0) {
                            // If SYN, direct to new server
                            installMicroflowRule(dpid, ipv4.getSourceAddress(), toAssignment.getServer());
                        } else {
                            // If non-SYN direct to old server
                            installMicroflowRule(dpid, ipv4.getSourceAddress(), fromAssignment.getServer());
                        }
                    }
                }
                break;
            case FLOW_REMOVED:
                Match removedMatch = ((OFFlowRemoved) msg).getMatch();

                Transition matchingTransition;

                // Remove rule from transition
                if (removedMatch.isExact(MatchField.IPV4_SRC)) {
                    // Microflow rule
                    matchingTransition = transitions.get(dpid).stream()
                            .filter(t -> t.getMicroflowMatches().contains(removedMatch))
                            .findFirst()
                            .get();

                    matchingTransition.getMicroflowMatches().remove(removedMatch);

                } else {
                    // Transition rule
                    matchingTransition = transitions.get(dpid).stream()
                            .filter(t -> t.getTransitionMatches().contains(removedMatch))
                            .findFirst()
                            .get();

                    matchingTransition.getTransitionMatches().remove(removedMatch);
                }

                // If transition is complete, install new permanent rules
                if (matchingTransition.getTransitionMatches().isEmpty()) {
                    for (Assignment assignment : matchingTransition.getTo()) {
                        installPermanentRule(dpid, assignment);
                    }
                }

                // When last microflow rule expires, remove transition
                if (matchingTransition.getMicroflowMatches().isEmpty()) {
                    transitions.get(dpid).remove(matchingTransition);
                }
                break;
        }
        return Command.CONTINUE;
    }

    // Transition helpers
    private void updateWeights(List<Double> weights) {
        config.setWeights(weights);
        AssignmentTree nextAssignmentTree = ServerLoadBalancerUtil.generateAssignmentTreeFewerTransitions(config, assignmentTree);
        for (DatapathId dpid : dpids) {
            List<Transition> transitionList = ServerLoadBalancerUtil.generateTransitions(assignmentTree, nextAssignmentTree);
            transitions.put(dpid, transitionList);
            for (Transition transition : transitionList) {
                startTransition(dpid, transition);
            }
        }
        assignmentTree = nextAssignmentTree;
    }

    private void startTransition(DatapathId dpid, Transition transition) {
        IOFSwitch mySwitch = switchManager.getSwitch(dpid);
        OFFactory factory = mySwitch.getOFFactory();
        OFActions actions = factory.actions();

        // Add new flow to controller
        IPv4AddressWithMask transitionPrefix = transition.prefix();
        Match transitionMatch = incomingMatch(dpid, transitionPrefix);

        ArrayList<OFAction> transitionActionList = new ArrayList<>();
        transitionActionList.add(actions.buildOutput()
                .setPort(OFPort.CONTROLLER)
                .setMaxLen(0xFFffFFff)
                .build());

        OFFlowAdd transitionFlowAdd = factory.buildFlowAdd()
                .setMatch(transitionMatch)
                .setPriority(TRANSITION_PRIORITY)
                .setActions(transitionActionList)
                .setHardTimeout(60)
                .setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
                .build();

        mySwitch.write(transitionFlowAdd);

        // Remove old flows
        for (Assignment assignment : transition.getFrom()) {
            Match incomingMatch = incomingMatch(dpid, assignment.getPrefix());

            OFFlowDeleteStrict incomingFlowDeleteStrict = factory.buildFlowDeleteStrict()
                    .setMatch(incomingMatch)
                    .setPriority(IN_PRIORITY)
                    .build();

            mySwitch.write(incomingFlowDeleteStrict);
        }
    }

    @Override
    public void requestTransition() {
        // TODO implement and debounce/throttle
    }

    // Rule helpers
    private Match incomingMatch(DatapathId dpid, IPv4AddressWithMask prefix) {
        IOFSwitch mySwitch = switchManager.getSwitch(dpid);
        OFFactory factory = mySwitch.getOFFactory();
        if (prefix.getMask().getInt() == 0) {
            // If prefix mask is 0.0.0.0 exclude IPV4_SRC from match
            return factory.buildMatch()
                    .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                    .setExact(MatchField.IPV4_DST, IPv4Address.of("1.1.1.1"))
                    .build();
        } else {
            return factory.buildMatch()
                    .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                    .setMasked(MatchField.IPV4_SRC, prefix)
                    .setExact(MatchField.IPV4_DST, IPv4Address.of("1.1.1.1"))
                    .build();
        }
    }

    private List<OFAction> incomingActionList(DatapathId dpid, int serverNum) {
        IOFSwitch mySwitch = switchManager.getSwitch(dpid);
        OFFactory factory = mySwitch.getOFFactory();
        OFActions actions = factory.actions();

        if (serverNum >= 0) {
            Server server = config.getServers().get(serverNum);
            ArrayList<OFAction> incomingActionList = new ArrayList<>();
            incomingActionList.add(actions.setDlSrc(MacAddress.of("00:00:0a:00:00:64"))); // 10.0.0.100 equiv. MAC
            incomingActionList.add(actions.setDlDst(server.getDlAddress()));
            incomingActionList.add(actions.setNwDst(server.getNwAddress()));
            incomingActionList.add(actions.buildOutput()
                    .setPort(OFPort.of(config.getCoreSwitch().getLoadBalanceTargets().get(server)))
                    .setMaxLen(0xFFffFFff)
                    .build());
            return incomingActionList;
        } else {
            return Collections.emptyList();
        }
    }

    private void installMicroflowRule(DatapathId dpid, IPv4Address src, int serverNum) {
        IOFSwitch mySwitch = switchManager.getSwitch(dpid);
        OFFactory factory = mySwitch.getOFFactory();
        Match incomingMatch = factory.buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_SRC, src)
                .setExact(MatchField.IPV4_DST, IPv4Address.of("1.1.1.1"))
                .build();

        List<OFAction> incomingActionList = incomingActionList(dpid, serverNum);

        OFFlowAdd incomingFlowAdd = factory.buildFlowAdd()
                .setMatch(incomingMatch)
                .setActions(incomingActionList)
                .setPriority(MICROFLOW_PRIORITY)
                .setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
                .setIdleTimeout(60)
                .build();

        mySwitch.write(incomingFlowAdd);
    }

    private void installPermanentRule(DatapathId dpid, Assignment assignment) {
        IOFSwitch mySwitch = switchManager.getSwitch(dpid);
        OFFactory factory = mySwitch.getOFFactory();

        IPv4AddressWithMask prefix = assignment.getPrefix();
        int server = assignment.getServer();

        Match incomingMatch = incomingMatch(dpid, prefix);

        List<OFAction> incomingActionList = incomingActionList(dpid, server);

        OFFlowAdd incomingFlowAdd = factory.buildFlowAdd()
                .setMatch(incomingMatch)
                .setActions(incomingActionList)
                .setPriority(IN_PRIORITY)
                .build();

        mySwitch.write(incomingFlowAdd);
    }

    // Switch Manager methods
    @Override
    public void switchAdded(DatapathId switchId) {
        IOFSwitch mySwitch = switchManager.getSwitch(switchId);
        OFFactory factory = mySwitch.getOFFactory();
        OFActions actions = factory.actions();

        // Remember switch
        dpids.add(switchId);

        // Delete all rules previously on this switch
        // TODO don't reset, continue where we left off
        mySwitch.write(factory.buildFlowDelete().build());

        // Handle incoming traffic
        for (Assignment assignment : assignmentTree.assignments()) {
            installPermanentRule(switchId, assignment);
        }

        // Handle outgoing traffic
        Match outgoingMatch = factory.buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .build();
        ArrayList<OFAction> outgoingActionList = new ArrayList<>();
        outgoingActionList.add(actions.setDlSrc(MacAddress.of("00:00:01:01:01:64")));
        outgoingActionList.add(actions.setDlDst(MacAddress.of("00:00:01:01:01:32")));
        outgoingActionList.add(actions.setNwSrc(IPv4Address.of("1.1.1.1")));
        outgoingActionList.add(actions.buildOutput()
                .setPort(OFPort.of(1))
                .setMaxLen(0xFFffFFff)
                .build());

        OFFlowAdd outgoingFlowAdd = factory.buildFlowAdd()
                .setMatch(outgoingMatch)
                .setActions(outgoingActionList)
                .setPriority(OUT_PRIORITY)
                .build();

        mySwitch.write(outgoingFlowAdd);
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        // Forget switch and reset transition information
        if (dpids.contains(switchId)) {
            dpids.remove(switchId);
            transitions.remove(switchId);
        }
    }

    @Override
    public void switchActivated(DatapathId switchId) {}

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {}

    @Override
    public void switchChanged(DatapathId switchId) {}

    // Server management methods
    @Override
    public void addServer(Server server) {
        this.servers.put(server.getId(), server);
    }

    @Override
    public Server getServer(int id) {
        return servers.get(id);
    }

    @Override
    public boolean removeServer(int id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void getStats() {

    }

    class LoadStatsCollector implements Runnable {

        private LinkedHashMap<Server, Long> prevLoad = new LinkedHashMap<>();

        public LoadStatsCollector() {
            for (Server server : config.getServers()) {
                prevLoad.put(server, 0L);
            }
        }

        @Override
        public void run() {
            for (DatapathId dpid : dpids) {
                IOFSwitch mySwitch = switchManager.getSwitch(dpid);
                OFFactory factory = mySwitch.getOFFactory();

                OFFlowStatsRequest request = factory.buildFlowStatsRequest().build();

                LinkedHashMap<Server, Long> load = new LinkedHashMap<>();
                for (Server server : config.getServers()) {
                    load.put(server, 0L);
                }

                try {
                    List<OFFlowStatsReply> replies = mySwitch.writeStatsRequest(request).get();
                    for (OFFlowStatsReply reply : replies) {
                        for (OFFlowStatsEntry entry : reply.getEntries()) {
                            try {
                                // Output action of this flow
                                OFActionOutput output = (OFActionOutput) entry.getActions().stream()
                                        .filter(x -> x instanceof OFActionOutput)
                                        .findFirst()
                                        .get();

                                // Server connected to this port
                                int portNumber = output.getPort().getPortNumber();
                                Server server = (Server) config.getCoreSwitch().getLoadBalanceTargets().entrySet().stream()
                                        .filter(x -> x.getValue() == portNumber)
                                        .findFirst()
                                        .get()
                                        .getKey();

                                // Record load
                                load.put(server, load.get(server) + entry.getByteCount().getValue());
                            } catch (NoSuchElementException e) {
                                // Dropped packet
                            }
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }

                for (Server server : load.keySet()) {
                    long number = load.get(server) - prevLoad.get(server);
                    logger.info(String.format("%s %d Mbits/s", server.getNwAddress(), number / 1024 / 1024));
                }

                // Update state
                prevLoad = load;
            }
        }
    }
}
