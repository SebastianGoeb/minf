package net.floodlightcontroller.proactiveloadbalancer;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.*;

import java.util.*;

class MessageBuilder {

    // Constants
    private static final U64 MEASUREMENT_COOKIE = U64.of(100);
    // TODO merge with priorities?
    private static final U64 LOAD_BALANCING_INGRESS_COOKIE = U64.of(100);
    // TODO merge with priorities?
    private static final U64 LOAD_BALANCING_EGRESS_COOKIE = U64.of(50);
    private static final U64 FALLBACK_COOKIE = U64.of(0);

    private static final int MEASUREMENT_PRIORITY = 100;
    // TODO merge with measurement priority?
    private static final int LOAD_BALANCING_INGRESS_PRIORITY = 100;
    // TODO merge with measurement priority?
    private static final int LOAD_BALANCING_EGRESS_PRIORITY = 50;
    private static final int FALLBACK_PRIORITY = 0;

    private static final int INGRESS_MEASUREMENT_TABLE_ID_OFFSET = 1;
    private static final int LOAD_BALANCING_TABLE_ID_OFFSET = 2;
    private static final int EGRESS_MEASUREMENT_TABLE_ID_OFFSET = 3;
    private static final int FORWARDING_TABLE_ID_OFFSET = 4;
    private static final short NUM_TABLES = 5;

    // TODO get at runtime?
    private static final MacAddress SWITCH_MAC = MacAddress.of("00:00:02:02:02:02");
    // TODO get at runtime?
    private static final Map<IPv4Address, MacAddress> SERVER_MACS = new HashMap<>();
    // TODO get at runtime?
    private static final Map<IPv4Address, MacAddress> DRIVER_MACS = new HashMap<>();


    static {
        SERVER_MACS.put(IPv4Address.of("10.1.1.2"), MacAddress.of("9a:b0:ad:56:d9:34"));
        SERVER_MACS.put(IPv4Address.of("10.1.1.3"), MacAddress.of("ee:3d:17:22:dc:2d"));
        SERVER_MACS.put(IPv4Address.of("10.1.2.2"), MacAddress.of("d6:a7:d3:02:9c:bd"));
        SERVER_MACS.put(IPv4Address.of("10.1.2.3"), MacAddress.of("fa:68:47:42:43:a1"));
        SERVER_MACS.put(IPv4Address.of("10.2.1.2"), MacAddress.of("82:91:b7:5a:63:28"));
        SERVER_MACS.put(IPv4Address.of("10.2.1.3"), MacAddress.of("2e:84:54:c3:76:d1"));
        SERVER_MACS.put(IPv4Address.of("10.2.2.2"), MacAddress.of("36:d5:29:59:63:2b"));
        SERVER_MACS.put(IPv4Address.of("10.2.2.3"), MacAddress.of("be:6c:7d:62:31:31"));
        SERVER_MACS.put(IPv4Address.of("10.3.1.2"), MacAddress.of("1e:ca:c3:13:44:43"));
        SERVER_MACS.put(IPv4Address.of("10.3.1.3"), MacAddress.of("f6:a6:11:17:44:36"));
        SERVER_MACS.put(IPv4Address.of("10.3.2.2"), MacAddress.of("06:70:d7:82:29:d0"));
        SERVER_MACS.put(IPv4Address.of("10.3.2.3"), MacAddress.of("ba:13:cf:89:66:18"));
        SERVER_MACS.put(IPv4Address.of("10.4.1.2"), MacAddress.of("6e:47:a6:68:df:fb"));
        SERVER_MACS.put(IPv4Address.of("10.4.1.3"), MacAddress.of("3e:ff:e6:a4:1e:1f"));
        SERVER_MACS.put(IPv4Address.of("10.4.2.2"), MacAddress.of("f6:2c:99:73:fc:d6"));
        SERVER_MACS.put(IPv4Address.of("10.4.2.3"), MacAddress.of("4e:b3:0c:da:61:23"));

        DRIVER_MACS.put(IPv4Address.of("10.5.1.2"), MacAddress.of("5c:b9:01:7b:45:50"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.3"), MacAddress.of("50:65:f3:e6:cf:d4"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.4"), MacAddress.of("5c:b9:01:7b:35:a0"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.5"), MacAddress.of("50:65:f3:e6:bf:14"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.6"), MacAddress.of("5c:b9:01:7b:d1:38"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.7"), MacAddress.of("50:65:f3:e6:bf:24"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.8"), MacAddress.of("5c:b9:01:7b:27:28"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.9"), MacAddress.of("50:65:f3:e6:bf:38"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.10"), MacAddress.of("5c:b9:01:7b:27:74"));
        DRIVER_MACS.put(IPv4Address.of("10.5.1.11"), MacAddress.of("50:65:f3:e6:9f:a8"));
    }

    // Utilities
    private static short getBaseTableId(DatapathId dpid) {
        return (short) ((dpid.getLong() - 1) * NUM_TABLES + 1);
    }

    private static TableId getIngressMeasurementTableId(DatapathId dpid) {
        return TableId.of(getBaseTableId(dpid) + INGRESS_MEASUREMENT_TABLE_ID_OFFSET);
    }

    private static TableId getLoadBalancingTableId(DatapathId dpid) {
        return TableId.of(getBaseTableId(dpid) + LOAD_BALANCING_TABLE_ID_OFFSET);
    }

    private static TableId getEgressMeasurementTableId(DatapathId dpid) {
        return TableId.of(getBaseTableId(dpid) + EGRESS_MEASUREMENT_TABLE_ID_OFFSET);
    }

    private static TableId getForwardingTableId(DatapathId dpid) {
        return TableId.of(getBaseTableId(dpid) + FORWARDING_TABLE_ID_OFFSET);
    }

    // Stub
    static List<OFFlowMod> addStubFlows(DatapathId dpid, OFFactory factory, IPv4Address vip) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);
        Objects.requireNonNull(vip);

        // OpenFlow
        OFInstructions instructions = factory.instructions();

        // Table ids
        TableId stubTableId = TableId.of(0);
        TableId ingressMeasurementTableId = getIngressMeasurementTableId(dpid);
        TableId loadBalancingTableId = getLoadBalancingTableId(dpid);

        // Add stub flows
        List<OFFlowMod> flowMods = new LinkedList<>();

        // Ingress
        Match ingressMatch = factory
                .buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_DST, vip)
                .build();

        flowMods.add(factory
                .buildFlowAdd()
                .setTableId(stubTableId)
                .setMatch(ingressMatch)
                .setInstructions(Collections.singletonList(instructions.gotoTable(ingressMeasurementTableId)))
                .build());

        // Egress
        Match egressMatch = factory
                .buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .build();

        flowMods.add(factory
                .buildFlowAdd()
                .setTableId(stubTableId)
                .setMatch(egressMatch)
                .setInstructions(Collections.singletonList(instructions.gotoTable(loadBalancingTableId)))
                .build());

        return flowMods;
    }

    // Load Balancing
    static List<OFFlowMod> deleteLoadBalancingFlows(DatapathId dpid, OFFactory factory) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);

        // Table ids
        TableId loadBalancingTableId = getLoadBalancingTableId(dpid);

        // Delete ingress and egress load balancing flows
        List<OFFlowMod> flowMods = new LinkedList<>();

        flowMods.add(factory
                .buildFlowDelete()
                .setTableId(loadBalancingTableId)
                .build());

        return flowMods;
    }

    static List<OFFlowMod> addLoadBalancingFlows(DatapathId dpid, OFFactory factory, IPv4Address vip, Set<Flow> flows) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);
        Objects.requireNonNull(vip);
        Objects.requireNonNull(flows);

        // OpenFlow
        OFActions actions = factory.actions();
        OFOxms oxms = factory.oxms();
        OFInstructions instructions = factory.instructions();

        // Table ids
        TableId loadBalancingTableId = getLoadBalancingTableId(dpid);
        TableId egressMeasurementTableId = getEgressMeasurementTableId(dpid);
        TableId forwardingTableId = getForwardingTableId(dpid);

        // Add ingress and egress load balancing flows
        List<OFFlowMod> flowMods = new LinkedList<>();
        for (Flow flow : flows) {
            IPv4Address dip = flow.getDip();
            IPv4AddressWithMask prefix = flow.getPrefix();

            // Ingress
            Match ingressMatch = factory
                    .buildMatch()
                    .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                    .setExact(MatchField.IPV4_DST, vip)
                    .setMasked(MatchField.IPV4_SRC, prefix)
                    .build();

            List<OFAction> ingressActionList = Arrays.asList(
                    actions.setField(oxms.ethSrc(SWITCH_MAC)),
                    actions.setField(oxms.ethDst(SERVER_MACS.get(dip))),
                    actions.setField(oxms.ipv4Dst(dip)));

            List<OFInstruction> ingressInstructionList = Arrays.asList(
                    instructions.applyActions(ingressActionList),
                    instructions.gotoTable(forwardingTableId));

            flowMods.add(factory
                    .buildFlowAdd()
                    .setTableId(loadBalancingTableId)
                    .setCookie(LOAD_BALANCING_INGRESS_COOKIE)
                    .setPriority(LOAD_BALANCING_INGRESS_PRIORITY)
                    .setMatch(ingressMatch)
                    .setInstructions(ingressInstructionList)
                    .build());

            // Egress
            Match egressMatch = factory
                    .buildMatch()
                    .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                    .setMasked(MatchField.IPV4_DST, prefix)
                    .build();

            List<OFAction> egressActionList = Arrays.asList(
                    actions.setField(oxms.ethSrc(SWITCH_MAC)),
                    actions.setField(oxms.ethDst(DRIVER_MACS.values().iterator().next())), // TODO runtime
                    actions.setField(oxms.ipv4Src(vip)));

            List<OFInstruction> egressInstructionList = Arrays.asList(
                    instructions.applyActions(egressActionList),
                    instructions.gotoTable(egressMeasurementTableId));

            flowMods.add(factory
                    .buildFlowAdd()
                    .setTableId(loadBalancingTableId)
                    .setCookie(LOAD_BALANCING_EGRESS_COOKIE)
                    .setPriority(LOAD_BALANCING_EGRESS_PRIORITY)
                    .setMatch(egressMatch)
                    .setInstructions(egressInstructionList)
                    .build());

            // TODO Transitions?
        }

        return flowMods;
    }

    // Traffic Measurement
    static List<OFFlowMod> deleteMeasurementFlows(DatapathId dpid, OFFactory factory) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);

        // Delete ingress and egress measurement flows
        List<OFFlowMod> flowMods = new LinkedList<>();

        // Ingress
        flowMods.add(factory
                .buildFlowDelete()
                .setTableId(getIngressMeasurementTableId(dpid))
                .setCookie(MEASUREMENT_COOKIE)
                .build());

        // Egress
        flowMods.add(factory
                .buildFlowDelete()
                .setTableId(getEgressMeasurementTableId(dpid))
                .setCookie(MEASUREMENT_COOKIE)
                .build());

        return flowMods;
    }

    static List<OFFlowMod> addMeasurementFlows(DatapathId dpid, OFFactory factory, PrefixTrie<Long> measurement) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);
        Objects.requireNonNull(measurement);

        // OpenFlow intsructions
        OFInstructions instructions = factory.instructions();

        // Table ids
        TableId ingressMeasurementTableId = getIngressMeasurementTableId(dpid);
        TableId loadBalancingTableId = getLoadBalancingTableId(dpid);
        TableId egressMeasurementTableId = getEgressMeasurementTableId(dpid);
        TableId forwardingTableId = getForwardingTableId(dpid);

        // Add ingress and egress measurement flows
        List<OFFlowMod> flowMods = new LinkedList<>();
        measurement.traversePreOrder((node, prefix) -> {
            if (node.isLeaf()) {
                Match match = factory
                        .buildMatch()
                        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                        .setMasked(MatchField.IPV4_DST, prefix)
                        .build();

                // Ingress
                flowMods.add(factory
                        .buildFlowAdd()
                        .setTableId(ingressMeasurementTableId)
                        .setCookie(MEASUREMENT_COOKIE)
                        .setPriority(MEASUREMENT_PRIORITY)
                        .setMatch(match)
                        .setInstructions(Collections.singletonList(instructions.gotoTable(loadBalancingTableId)))
                        .build());

                // Egress
                flowMods.add(factory
                        .buildFlowAdd()
                        .setTableId(egressMeasurementTableId)
                        .setCookie(MEASUREMENT_COOKIE)
                        .setPriority(MEASUREMENT_PRIORITY)
                        .setMatch(match)
                        .setInstructions(Collections.singletonList(instructions.gotoTable(forwardingTableId)))
                        .build());
            }
        });

        return flowMods;
    }

    static List<OFFlowMod> deleteFallbackFlows(DatapathId dpid, OFFactory factory) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);

        // Delete ingress and egress fallback flows
        List<OFFlowMod> flowMods = new LinkedList<>();

        // Ingress
        flowMods.add(factory
                .buildFlowDelete()
                .setTableId(getIngressMeasurementTableId(dpid))
                .setCookie(FALLBACK_COOKIE)
                .build());

        // Egress
        flowMods.add(factory
                .buildFlowDelete()
                .setTableId(getEgressMeasurementTableId(dpid))
                .setCookie(FALLBACK_COOKIE)
                .build());

        return flowMods;
    }

    static List<OFFlowMod> addFallbackFlows(DatapathId dpid, OFFactory factory) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);

        // OpenFlow intsructions
        OFInstructions instructions = factory.instructions();

        // Table ids
        TableId loadBalancingTableId = getLoadBalancingTableId(dpid);
        TableId forwardingTableId = getForwardingTableId(dpid);

        // Delete ingress and egress fallback flows
        List<OFFlowMod> flowMods = new LinkedList<>();

        // Ingress
        flowMods.add(factory
                .buildFlowAdd()
                .setTableId(getIngressMeasurementTableId(dpid))
                .setCookie(FALLBACK_COOKIE)
                .setPriority(FALLBACK_PRIORITY)
                .setInstructions(Collections.singletonList(instructions.gotoTable(loadBalancingTableId)))
                .build());

        // Egress
        flowMods.add(factory
                .buildFlowAdd()
                .setTableId(getEgressMeasurementTableId(dpid))
                .setCookie(FALLBACK_COOKIE)
                .setPriority(FALLBACK_PRIORITY)
                .setInstructions(Collections.singletonList(instructions.gotoTable(forwardingTableId)))
                .build());

        return flowMods;
    }

    static OFFlowStatsRequest requestIngressMeasurementFlowStats(DatapathId dpid, OFFactory factory) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);

        return factory
                .buildFlowStatsRequest()
                .setMatch(factory.buildMatch().build())
                .setTableId(getIngressMeasurementTableId(dpid))
                .setCookie(MEASUREMENT_COOKIE)
                .build();
    }

    static OFFlowStatsRequest requestEgressMeasurementFlowStats(DatapathId dpid, OFFactory factory) {
        // Preconditions
        Objects.requireNonNull(dpid);
        Objects.requireNonNull(factory);

        return factory
                .buildFlowStatsRequest()
                .setMatch(factory.buildMatch().build())
                .setTableId(getEgressMeasurementTableId(dpid))
                .setCookie(MEASUREMENT_COOKIE)
                .build();
    }
}
