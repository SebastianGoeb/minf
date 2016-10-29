package net.floodlightcontroller.serverloadbalancer;

import com.fasterxml.jackson.annotation.JsonGetter;
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
import net.floodlightcontroller.serverloadbalancer.assignment.Assignment;
import net.floodlightcontroller.serverloadbalancer.assignment.AssignmentTree;
import net.floodlightcontroller.serverloadbalancer.assignment.AssignmentTree.Changes;
import net.floodlightcontroller.serverloadbalancer.network.*;
import net.floodlightcontroller.serverloadbalancer.web.ServerLoadBalancerWebRoutable;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.OFFlowAdd.Builder;
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
import java.util.stream.Collectors;

public class ServerLoadBalancer implements IFloodlightModule, IOFMessageListener, IOFSwitchListener, IServerLoadBalancerService {

    private static final short TCP_SYN = 0x2;

    private static final int IN_PRIORITY = 600;
    private static final int DROP_PRIORITY = 500;
    private static final int OUT_PRIORITY = 400;

    // Utility fields
    protected static Logger log = LoggerFactory.getLogger(ServerLoadBalancer.class);

    // Services
    protected IFloodlightProviderService floodlightProvider;
    protected IThreadPoolService threadPoolService;
    protected IOFSwitchService switchManager;
    protected IRestApiService restApiService;

    // Configuration
    private int maxPrefixLength;

    // Network information
    private List<Switch> switches;
    private List<Server> servers;

    // State
    // TODO when multiple switches report expired transitions, a single assignment tree will have duplicate deletions
    private AssignmentTree assignmentTree;
    private Map<DatapathId, List<Assignment>> microflowAssignments;

    // ----------------------------------------------------------------
    // - IOFMessageListener methods
    // ----------------------------------------------------------------
    @Override
    public String getName() {
        return ServerLoadBalancer.class.getSimpleName();
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
        DatapathId dpid = iofSwitch.getId();
        Switch sw = switches.stream()
                .filter(aSwitch -> Objects.equals(aSwitch.getDpid(), dpid))
                .findFirst()
                .orElse(null);
        if (sw == null) {
            return Command.CONTINUE;
        }

        switch (msg.getType()) {
            case PACKET_IN:
                log.info("Packet in");
                Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
                if (EthType.IPv4.getValue() == eth.getEtherType()) {
                    IPv4 ipv4 = (IPv4) eth.getPayload();

                    if (ipv4.getProtocol().equals(IpProtocol.TCP)) {
                        // Handle TCP packets for transitioning IP prefixes
                        TCP tcp = (TCP) ipv4.getPayload();
                        IPv4Address src = ipv4.getSourceAddress();

                        ForwardingTarget target = assignmentTree.findTarget(src);
                        log.info("Flow for " + target);
                        if (target instanceof TransitionTarget) {
                            log.info("Flags " + Integer.toBinaryString(tcp.getFlags()));
                            log.info("SYN " + ((tcp.getFlags() & TCP_SYN) != 0));
                            TransitionTarget transitionTarget = (TransitionTarget) target;
                            ForwardingTarget microflowTarget = (tcp.getFlags() & TCP_SYN) != 0
                                    ? transitionTarget.getNewTarget()
                                    : transitionTarget.getOldTarget();
                            Assignment microflowAssignment = new Assignment(
                                    IPv4AddressWithMask.of(src, IPv4Address.NO_MASK),
                                    microflowTarget);
                            log.info("Assigned to " + microflowAssignment);
                            installRule(sw, microflowAssignment);
                            microflowAssignments.get(dpid).add(microflowAssignment);
                        }
                    }
                }
                break;
            case FLOW_REMOVED:
                Match removedMatch = ((OFFlowRemoved) msg).getMatch();
                if (removedMatch.isExact(MatchField.IPV4_SRC)) {
                    // Remove microflow rule
                    IPv4Address removedSrc = removedMatch.get(MatchField.IPV4_SRC);
                    microflowAssignments.get(dpid)
                            .removeIf(assignment -> assignment.getPrefix().getValue().equals(removedSrc));
                } else {
                    IPv4AddressWithMask removedSrc = (IPv4AddressWithMask) removedMatch.getMasked(MatchField.IPV4_SRC);

                    // TODO technically unsafe. Should use prefix with mask, but as long as the tree is well constructed that won't make a difference
                    ForwardingTarget target = assignmentTree.findTarget(removedSrc.getValue());
                    if (target instanceof TransitionTarget) {
                        TransitionTarget transitionTarget = (TransitionTarget) target;
                        // Update the assignment tree
                        Changes changes = assignmentTree.assignPrefix(removedSrc, transitionTarget.getNewTarget());
                        // Delete any old rules in the switch, except the one that triggered this method
                        changes.deletions.remove(new Assignment(removedSrc, transitionTarget.getOldTarget()));
                        for (Assignment deletion : changes.deletions) {
                            removeRule(sw, deletion);
                        }
                        // Add any new rules to the switch
                        for (Assignment addition : changes.additions) {
                            installRule(sw, addition);
                        }
                        // Delete any microflows to the new target in the switch
                        List<Assignment> removedMicroflowAssignments = microflowAssignments.get(dpid).stream()
                                .filter(a -> a.getPrefix().equals(removedSrc)
                                        && a.getTarget().equals(transitionTarget.getNewTarget()))
                                .collect(Collectors.toList());
                        for (Assignment removedAssignment : removedMicroflowAssignments) {
                            removeRule(sw, removedAssignment);
                        }
                    }
                }
                break;
        }
        return Command.STOP;
    }

    // ----------------------------------------------------------------
    // - IFloodlightModule methods
    // ----------------------------------------------------------------
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

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
        switchManager = context.getServiceImpl(IOFSwitchService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        // Configuration
        // TODO move?
        maxPrefixLength = 3;

        // Network
        switches = new ArrayList<>();
        servers = new ArrayList<>();

        // State
        assignmentTree = new AssignmentTree();
        microflowAssignments = new HashMap<>();

        // Services
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        switchManager.addOFSwitchListener(this);
        restApiService.addRestletRoutable(new ServerLoadBalancerWebRoutable());
    }

    // ----------------------------------------------------------------
    // - IOFSwitchListener methods
    // ----------------------------------------------------------------
    @Override
    public void switchAdded(DatapathId switchId) {
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        log.info("Switch disconnected  " + switchId);
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        log.info("Switch activated " + switchId);
        // If switch exists, setup what we can
        switches.stream()
                .filter(sw -> sw.getDpid().equals(switchId))
                .findFirst()
                .ifPresent(this::setupSwitch);
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        Switch sw = switches.stream()
                .filter(s -> s.getDpid().equals(switchId))
                .findFirst()
                .orElse(null);
        if (sw == null) {
            log.info("Port changed on unknown switch");
            return;
        }

        IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
        OFFactory factory = switchBackend.getOFFactory();

        if (port.getName().contains("router")) {
            if (switchBackend.portEnabled(port.getName())) {
                // Handle other (outgoing) traffic
                Match outgoingMatch = factory.buildMatch()
                        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                        .build();
                OFFlowAdd outgoingFlowAdd = factory.buildFlowAdd()
                        .setMatch(outgoingMatch)
                        .setActions(outgoingActionList(sw))
                        .setPriority(OUT_PRIORITY)
                        .build();
                switchBackend.write(outgoingFlowAdd);
            }
        } else {
            log.info("Installing rules for target " + sw.getTarget(port.getName()));
            // Handle incoming traffic
            LoadBalanceTarget portTarget = sw.getTarget(port.getName());
            for (Assignment assignment : assignmentTree.assignments()) {
                ForwardingTarget target = assignment.getTarget();
                if (targetContainsOtherTarget(target, portTarget) && targetKnownAndConnected(target, sw)) {
                    installRule(sw, assignment);
                }
            }
        }
    }

    @Override
    public void switchChanged(DatapathId switchId) {
    }

    private void setupSwitch(Switch sw) {
        IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
        OFFactory factory = switchBackend.getOFFactory();

        // Delete all rules previously on this switch
        switchBackend.write(factory.buildFlowDelete().build());

        // Handle incoming traffic where possible
        for (Assignment assignment : assignmentTree.assignments()) {
            if (targetKnownAndConnected(assignment.getTarget(), sw)) {
                installRule(sw, assignment);
            }
        }

        // Drop remaining incoming traffic
        switchBackend.write(factory.buildFlowAdd()
                .setMatch(incomingMatch(switchBackend, IPv4AddressWithMask.NONE))
                .setActions(Collections.emptyList())
                .setPriority(DROP_PRIORITY)
                .build());

        // Handle other (outgoing traffic) if possible
        if (switchBackend.getPort("router" + sw.getId()) != null) {
            Match outgoingMatch = factory.buildMatch()
                    .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                    .build();
            OFFlowAdd outgoingFlowAdd = factory.buildFlowAdd()
                    .setMatch(outgoingMatch)
                    .setActions(outgoingActionList(sw))
                    .setPriority(OUT_PRIORITY)
                    .build();
            switchBackend.write(outgoingFlowAdd);
        }
        log.info("Switch setup " + sw.getDpid());
    }

    public void setupTarget(Switch sw, String portName) {
        LoadBalanceTarget target = sw.getTarget(portName);
        assignmentTree.assignments().stream()
                .filter(ass -> targetContainsOtherTarget(ass.getTarget(), target))
                .filter(ass -> targetKnownAndConnected(ass.getTarget(), sw))
                .forEach(ass -> installRule(sw, ass));
    }

    public boolean targetKnownAndConnected(ForwardingTarget target, Switch sw) {
        IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
        if (target == ForwardingTarget.NONE) {
            // If the target is to drop, it's reachable
            return true;
        } else if (target instanceof LoadBalanceTarget) {
            // If the target is known and connected, it's reachable
            LoadBalanceTarget loadBalanceTarget = (LoadBalanceTarget) target;
            return sw.getPort(loadBalanceTarget) != null && switchBackend.portEnabled(sw.getPort(loadBalanceTarget));
        } else if (target instanceof TransitionTarget) {
            // If both the targets are known and connected, the transition target is reachable
            TransitionTarget transitionTarget = (TransitionTarget) target;
            return targetKnownAndConnected(transitionTarget.getOldTarget(), sw)
                    && targetKnownAndConnected(transitionTarget.getOldTarget(), sw);
        }
        return false;
    }

    public boolean targetContainsOtherTarget(ForwardingTarget target, LoadBalanceTarget involvedTarget) {
        // The compact form is incredibly confusing, hence the use of "if (x) return true;"
        if (target.equals(involvedTarget)) {
            return true;
        } else if (target instanceof TransitionTarget) {
            TransitionTarget transitionTarget = (TransitionTarget) target;
            if (transitionTarget.getOldTarget().equals(involvedTarget)
                    || transitionTarget.getNewTarget().equals(involvedTarget)) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------
    // - IServerLoadBalancerService methods
    // ----------------------------------------------------------------
    @Override
    public void addServer(Server server) {
        servers.add(server);
        log.info("Server added " + server.toString());
    }

    @Override
    public List<Server> getServers() {
        return servers;
    }

    @Override
    public void removeServer(Server server) {
        // Remove server from configuration
        servers.remove(server);
        switches.forEach(sw -> sw.removeTarget(server));

        // Update assignment tree and collect changes
        Changes changes = assignmentTree.assignments().stream()
                .filter(assignment -> Objects.equals(assignment.getTarget(), server))
                .map(Assignment::getPrefix)
                .map(prefix -> assignmentTree.assignPrefix(prefix, ForwardingTarget.NONE))
                .reduce(new Changes(), Changes::add);

        // Delete any old rules from switches
        for (Assignment deletion : changes.deletions) {
            for (Switch s : switches) {
                DatapathId dpid = s.getDpid();
                IOFSwitch sw = switchManager.getSwitch(dpid);
                IPv4AddressWithMask prefix = deletion.getPrefix();
                ForwardingTarget target = deletion.getTarget();

                if (target instanceof TransitionTarget) {
                    TransitionTarget transitionTarget = (TransitionTarget) target;

                    // Remove all associated microflows
                    List<Assignment> removedMicroflowAssignments = microflowAssignments.get(dpid).stream()
                            .filter(a -> a.getPrefix().equals(prefix)
                                    && (a.getTarget().equals(transitionTarget.getOldTarget())
                                    || a.getTarget().equals(transitionTarget.getNewTarget())))
                            .collect(Collectors.toList());
                    for (Assignment removedAssignment : removedMicroflowAssignments) {
                        removeRule(s, removedAssignment);
                    }
                }
                removeRule(s, deletion);
            }
        }
        log.info("Server removed" + server.toString());
    }

    @Override
    public void addSwitch(Switch sw) {
        if (switches.contains(sw)) {
            removeSwitch(sw);
        }
        switches.add(sw);
        microflowAssignments.put(sw.getDpid(), new ArrayList<>());
        IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
        // If switch exists, setup what we can
        if (switchBackend != null) {
            setupSwitch(sw);
        }
        log.info("Switch added " + sw);
    }

    @Override
    public List<Switch> getSwitches() {
        return switches;
    }

    @Override
    public void removeSwitch(Switch sw) {
        switches.stream()
                .filter(s -> s.getTargets().values().contains(sw))
                .forEach(s -> s.removeTarget(sw));
        switches.remove(sw);
        microflowAssignments.remove(sw.getDpid());
        log.info("Switch removed " + sw);
    }

    @Override
    public void addTarget(Switch sw, String portName, LoadBalanceTarget target) {
        sw.addTarget(portName, target);
        IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
        if (switchBackend != null && switchBackend.portEnabled(portName)) {
            setupTarget(sw, portName);
        }
        log.info("Target added " + sw + " -> " + target);
    }

    // ----------------------------------------------------------------
    // - Transition methods
    // ----------------------------------------------------------------
    @Override
    public void requestTransition(boolean fromCurrent) {
        List<LoadBalanceTarget> targets = switches.stream()
                .map(sw -> sw.getTargets().values())
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        // Generate new assignment tree
        if (fromCurrent) {
            AssignmentTree newAssignmentTree = ServerLoadBalancerUtil.generateIPv4AssignmentTree(targets, maxPrefixLength, assignmentTree);
            Changes changes = assignmentTree.transitionTo(newAssignmentTree);
            for (Switch sw : switches) {
                // Delete any old rules from switches
                IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
                if (switchBackend != null) {
                    for (Assignment deletion : changes.deletions) {
                        removeRule(sw, deletion);
                    }

                    for (Assignment addition : changes.additions) {
                        installRule(sw, addition);
                    }
                }
            }
            log.info("Transition started");
        } else {
            assignmentTree = ServerLoadBalancerUtil.generateIPv4AssignmentTree(targets, maxPrefixLength);
            for (Switch sw : switches) {
                microflowAssignments.get(sw.getDpid()).clear();
                IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
                // If switch is connected, reset switch
                if (switchBackend != null) {
                    setupSwitch(sw);
                }
            }
            log.info("Reset complete");
        }
    }

    @Override
    public void setMaxPrefixLength() {
        int minPrefixes = (int) Math.ceil(servers.size() * 8.0 / 7.0);
        int smallestAllowedMaxPrefixLength = 3;
        for (int i = smallestAllowedMaxPrefixLength; i <= 32; i++) {
            if (1 << i >= minPrefixes) {
                maxPrefixLength = i;
                return;
            }
        }
        throw new IllegalStateException("no maxPrefixLength from 3-32 was enough to accomodate all these servers");
    }

    @Override
    public void setMaxPrefixLength(int maxPrefixLength) {
        if (maxPrefixLength < 3 || 32 < maxPrefixLength) {
            throw new IllegalArgumentException("Max Prefix Length must be in range [3, 32] was " + maxPrefixLength);
        }
        this.maxPrefixLength = maxPrefixLength;
    }

    // ----------------------------------------------------------------
    // - Rule helpers
    // ----------------------------------------------------------------
    private Match incomingMatch(IOFSwitch sw, IPv4AddressWithMask prefix) {
        OFFactory factory = sw.getOFFactory();

        Match.Builder builder = factory.buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_DST, IPv4Address.of("1.1.1.1"));

        if (prefix.getMask() == IPv4Address.NO_MASK) {
            // prefix is /32
            builder.setExact(MatchField.IPV4_SRC, prefix.getValue());
        } else if (prefix.getMask() != IPv4Address.FULL_MASK) {
            // prefix is /1 or smaller
            builder.setMasked(MatchField.IPV4_SRC, prefix);
        }

        return builder.build();
    }

    private List<OFAction> incomingActionList(Switch sw, ForwardingTarget target) {
        IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
        OFActions actions = switchBackend.getOFFactory().actions();

        if (target instanceof TransitionTarget) {
            return Arrays.asList(
                    actions.setDlSrc(MacAddress.of("00:00:0a:00:00:64")), // 10.0.0.100 equiv. MAC
                    actions.buildOutput()
                            .setPort(OFPort.CONTROLLER)
                            .setMaxLen(0xFFffFFff)
                            .build());
        } else if (target instanceof LoadBalanceTarget) {
            ArrayList<OFAction> actionList = new ArrayList<>();
            actionList.add(actions.setDlSrc(MacAddress.of("00:00:0a:00:00:64"))); // 10.0.0.100 equiv. MAC

            if (target instanceof Server) {
                Server serverTarget = (Server) target;
                actionList.add(actions.setDlDst(serverTarget.getDlAddress()));
                actionList.add(actions.setNwDst(serverTarget.getNwAddress()));
            }

            actionList.add(actions.buildOutput()
                    .setPort(switchBackend.getPort(sw.getPort((LoadBalanceTarget) target)).getPortNo())
                    .setMaxLen(0xFFffFFff)
                    .build());
            return actionList;
        } else {
            return Collections.emptyList();
        }
    }

    private List<OFAction> outgoingActionList(Switch sw) {
        IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
        OFActions actions = switchBackend.getOFFactory().actions();

        return Arrays.asList(
                actions.setDlSrc(MacAddress.of("00:00:01:01:01:32")),
                actions.setDlDst(MacAddress.of("00:00:01:01:01:64")),
                actions.setNwSrc(IPv4Address.of("1.1.1.1")),
                actions.buildOutput()
                        .setPort(switchBackend.getPort("router" + sw.getId()).getPortNo())
                        .setMaxLen(0xFFffFFff)
                        .build());
    }

    private int incomingPriority(Assignment assignment) {
        IPv4AddressWithMask prefix = assignment.getPrefix();
        ForwardingTarget target = assignment.getTarget();

        if (target instanceof TransitionTarget || target instanceof LoadBalanceTarget) {
            return IN_PRIORITY + prefix.getMask().asCidrMaskLength();
        } else {
            throw new IllegalArgumentException("Not a valid target " + target);
        }
    }

    private void installRule(Switch sw, Assignment assignment) {
        IOFSwitch iofSwitch = switchManager.getSwitch(sw.getDpid());
        OFFactory factory = iofSwitch.getOFFactory();

        IPv4AddressWithMask prefix = assignment.getPrefix();
        ForwardingTarget target = assignment.getTarget();

        Builder builder = factory.buildFlowAdd()
                .setMatch(incomingMatch(iofSwitch, prefix))
                .setActions(incomingActionList(sw, target))
                .setPriority(incomingPriority(assignment));

        if (target instanceof TransitionTarget) {
            builder.setHardTimeout(60)
                    .setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM));
        } else if (prefix.getMask().asCidrMaskLength() == 32) {
            builder.setIdleTimeout(60)
                    .setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM));
        }

        iofSwitch.write(builder.build());
    }

    private void removeRule(Switch sw, Assignment assignment) {
        IOFSwitch switchBackend = switchManager.getSwitch(sw.getDpid());
        OFFactory factory = switchBackend.getOFFactory();

        IPv4AddressWithMask prefix = assignment.getPrefix();

        switchBackend.write(factory.buildFlowDeleteStrict()
                .setMatch(incomingMatch(switchBackend, prefix))
                .setPriority(incomingPriority(assignment))
                .build());
    }

    // ----------------------------------------------------------------
    // - Stats methods
    // ----------------------------------------------------------------
    @Override
    public Stats getStats(Switch s) {
        Stats stats = new Stats();
        for (Server server : servers) {
            stats.load.put(server, 0L);
        }

        IOFSwitch switchBackend = switchManager.getSwitch(s.getDpid());
        OFFactory factory = switchBackend.getOFFactory();

        OFFlowStatsRequest request = factory.buildFlowStatsRequest().build();


        try {
            List<OFFlowStatsReply> replies = switchBackend.writeStatsRequest(request).get();
            for (OFFlowStatsReply reply : replies) {
                for (OFFlowStatsEntry entry : reply.getEntries()) {
                    // Find output action of this flow
                    OFActionOutput output = (OFActionOutput) entry.getActions().stream()
                            .filter(x -> x instanceof OFActionOutput)
                            .findFirst()
                            .orElse(null);

                    if (output != null) {
                        // Target connected to this port
                        OFPortDesc portDesc = switchBackend.getPort(output.getPort());
                        if (!Objects.equals(output.getPort(), OFPort.CONTROLLER)) {
                            LoadBalanceTarget target = s.getTarget(portDesc.getName());
                            // Record load
                            if (target != null) {
                                stats.load.put(target, stats.load.get(target) + entry.getByteCount().getValue());
                            }
                        }
                    }
                    stats.numRules += 1;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Unable to get stats for switch %s: %s", switchBackend, e.getMessage());
            return null;
        }
        return stats;
    }

    public class Stats {
        private int numRules;
        private Map<LoadBalanceTarget, Long> load;

        public Stats() {
            numRules = 0;
            load = new HashMap<>();
        }

        @JsonGetter("rules")
        public int getNumRules() {
            return numRules;
        }

        @JsonGetter("bytes")
        public Map<Integer, Long> getJsonLoad() {
            return load.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().getId(),
                            e -> e.getValue()
                    ));
        }

        public long getLoad(LoadBalanceTarget target) {
            return load.get(target);
        }
    }
}
