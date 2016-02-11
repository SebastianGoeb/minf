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
import java.util.concurrent.TimeUnit;

public class ServerLoadBalancer implements IFloodlightModule, IOFMessageListener, IOFSwitchListener {

    private static final short TCP_SYN = 0x2;
    private static final short TCP_ACK = 0x10;

    private static final int MICROFLOW_PRIORITY = 800;
    private static final int TRANSITION_PRIORITY = 700;
    private static final int IN_PRIORITY = 600;
    private static final int OUT_PRIORITY = 500;

    protected static Logger logger;
    protected IFloodlightProviderService floodlightProvider;
    protected IThreadPoolService threadPoolService;
    protected IOFSwitchService switchManager;
    private IOFSwitch mySwitch;
    private ScheduledFuture<?> loadStatsFuture;

    // Configuration
    private Config config;

    // State
    private AssignmentTree assignmentTree;
    private List<Transition> transitions;

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

    @Override
    public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
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
                        Transition matchingTransition = transitions.stream()
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

                        // If SYN, direct to new server, if non-SYN direct to old server
                        if ((tcp.getFlags() & TCP_SYN) != 0) {
                            installMicroflowRule(ipv4.getSourceAddress(), toAssignment.getServer());
                        } else {
                            installMicroflowRule(ipv4.getSourceAddress(), fromAssignment.getServer());
                        }
                    }
                }
                break;
            case FLOW_REMOVED:
                Match removedMatch = ((OFFlowRemoved) msg).getMatch();

                // Find transition with overall prefix matching removed flow
                try {
                    Transition matchingTransition = transitions.stream()
                            .filter(t -> incomingMatch(t.prefix()).equals(removedMatch))
                            .findFirst()
                            .get();

                    // Install flow for new assignment
                    for (Assignment assignment : matchingTransition.getTo()) {
                        installPermanentRule(assignment);
                    }

                    // Remove transition
                    transitions.remove(matchingTransition);
                } catch (NoSuchElementException e) {
                    // No such transition. This happens when a switch comes online that was
                    // in the process of transitioning some prefixes
                }
                break;
        }
        return Command.CONTINUE;
    }

    private void handleTCP(IPv4 ipv4, TCP tcp) {
    }

    private void installMicroflowRule(IPv4Address src, int serverNum) {
//        ServerDesc server = config.getServers().get(serverNum);
        OFFactory factory = mySwitch.getOFFactory();
//        OFActions actions = factory.actions();
        Match incomingMatch = factory.buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_SRC, src)
                .setExact(MatchField.IPV4_DST, IPv4Address.of("1.1.1.1"))
                .build();

        List<OFAction> incomingActionList = incomingActionList(serverNum);
//        incomingActionList.add(actions.setDlSrc(MacAddress.of("00:00:0a:00:00:64"))); // 10.0.0.100 equiv. MAC
//        incomingActionList.add(actions.setDlDst(server.getDlAddress()));
//        incomingActionList.add(actions.setNwDst(server.getNwAddress()));
//        incomingActionList.add(actions.buildOutput()
//                .setPort(OFPort.of(config.getCoreSwitch().getLoadBalanceTargets().get(server)))
//                .setMaxLen(0xFFffFFff)
//                .build());

        OFFlowAdd incomingFlowAdd = factory.buildFlowAdd()
                .setMatch(incomingMatch)
                .setActions(incomingActionList)
                .setPriority(MICROFLOW_PRIORITY)
                .setIdleTimeout(60)
                .build();

        mySwitch.write(incomingFlowAdd);
    }

    private void installPermanentRule(Assignment assignment) {
        OFFactory factory = mySwitch.getOFFactory();
//        OFActions actions = factory.actions();

        IPv4AddressWithMask prefix = assignment.getPrefix();
        int server = assignment.getServer();

        Match incomingMatch = incomingMatch(prefix);

        List<OFAction> incomingActionList = incomingActionList(server);

        OFFlowAdd incomingFlowAdd = factory.buildFlowAdd()
                .setMatch(incomingMatch)
                .setActions(incomingActionList)
                .setPriority(IN_PRIORITY)
                .build();

        mySwitch.write(incomingFlowAdd);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> list = new ArrayList<>();
        list.add(IFloodlightProviderService.class);
        list.add(IThreadPoolService.class);
        return list;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        logger = LoggerFactory.getLogger(ServerLoadBalancer.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        // Init configuration
        // TODO move?
        config = new Config()
                .setWeights(Arrays.asList(1d, 1d))
                .setMaxPrefixLength(3)
                .setCoreSwitch(new SwitchDesc())
                .addServer(new ServerDesc(IPv4Address.of("10.0.0.1"), MacAddress.of("00:00:0A:00:00:01")), 2)
                .addServer(new ServerDesc(IPv4Address.of("10.0.0.2"), MacAddress.of("00:00:0A:00:00:02")), 3)
                .setLoadStatsInterval(1);

        // Floodlight Provice Service
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);

        // Switch Service
        switchManager = context.getServiceImpl(IOFSwitchService.class);
        switchManager.addOFSwitchListener(this);

        // Thread Pool Service
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
        loadStatsFuture = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(
                new LoadStatsCollector(),
                config.getLoadStatsInterval(),
                config.getLoadStatsInterval(),
                TimeUnit.SECONDS);

        // Init state
        assignmentTree = ServerLoadBalancerUtil.generateAssignmentTreeOptimal(config);
        transitions = new ArrayList<>();

        logger.info("startup complete");
    }

    private void updateWeights(List<Double> weights) {
        config.setWeights(weights);
        AssignmentTree nextAssignmentTree = ServerLoadBalancerUtil.generateAssignmentTreeOptimal(config);
        transitions = ServerLoadBalancerUtil.generateTransitions(assignmentTree, nextAssignmentTree);

        // TODO refactor to remember both and switch when all transitions complete or reset
        assignmentTree = nextAssignmentTree;
        transitions.forEach(this::startTransition);
    }

    private void startTransition(Transition transition) {
        OFFactory factory = mySwitch.getOFFactory();
        OFActions actions = factory.actions();

        // Add new flow to controller
        IPv4AddressWithMask transitionPrefix = transition.prefix();
        Match transitionMatch = incomingMatch(transitionPrefix);

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
            Match incomingMatch = incomingMatch(assignment.getPrefix());

            OFFlowDeleteStrict incomingFlowDeleteStrict = factory.buildFlowDeleteStrict()
                    .setMatch(incomingMatch)
                    .setPriority(IN_PRIORITY)
                    .build();

            mySwitch.write(incomingFlowDeleteStrict);
        }
    }

    private Match incomingMatch(IPv4AddressWithMask prefix) {
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

    private List<OFAction> incomingActionList(int serverNum) {
        OFFactory factory = mySwitch.getOFFactory();
        OFActions actions = factory.actions();

        if (serverNum >= 0) {
            ServerDesc server = config.getServers().get(serverNum);
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

    @Override
    public void switchAdded(DatapathId switchId) {
        mySwitch = switchManager.getSwitch(switchId);
        OFFactory factory = mySwitch.getOFFactory();
        OFActions actions = factory.actions();

        // Delete all rules previously on this switch
        // TODO don't reset, continue where we left off
        mySwitch.write(factory.buildFlowDelete().build());

        // Handle incoming traffic
        assignmentTree.assignments().forEach(this::installPermanentRule);

        // Handle outgoing traffic
        Match outgoingMatch = factory.buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .build();
        ArrayList<OFAction> outgoingActionList = new ArrayList<>();
        outgoingActionList.add(actions.setDlSrc(MacAddress.of("00:00:01:01:01:32")));
        outgoingActionList.add(actions.setDlDst(MacAddress.of("00:00:01:01:01:64")));
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

        // TODO remove
//        updateWeights(Arrays.asList(1d, 1d));
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        // Forget switch and reset transition information
        if (mySwitch.getId().equals(switchId)) {
            mySwitch = null;
            transitions.clear();
        }
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        // TODO Auto-generated method stub

    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        // TODO Auto-generated method stub

    }

    @Override
    public void switchChanged(DatapathId switchId) {
        // TODO Auto-generated method stub

    }

    class LoadStatsCollector implements Runnable {

        private LinkedHashMap<ServerDesc, Long> prevLoad = new LinkedHashMap<>();

        public LoadStatsCollector() {
            for (ServerDesc server : config.getServers()) {
                prevLoad.put(server, 0L);
            }
        }

        @Override
        public void run() {
            OFFactory factory = mySwitch.getOFFactory();

            OFFlowStatsRequest request = factory.buildFlowStatsRequest().build();

            LinkedHashMap<ServerDesc, Long> load = new LinkedHashMap<>();
            for (ServerDesc server : config.getServers()) {
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
                            ServerDesc server = (ServerDesc) config.getCoreSwitch().getLoadBalanceTargets().entrySet().stream()
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
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            for (ServerDesc server : load.keySet()) {
                long number = load.get(server) - prevLoad.get(server);
                logger.info(String.format("%s %d Mbits/s", server.getNwAddress(), number / 1024 / 1024));
            }

            // Update state
            prevLoad = load;
        }
    }
}
