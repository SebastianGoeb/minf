package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.*;

import java.util.*;

class FlowModBuilder {

    // Constants
    // TODO get at runtime?
    private static final MacAddress SWITCH_MAC = MacAddress.of("00:00:02:02:02:02");
    // TODO get at runtime?
    private static final Map<IPv4Address, MacAddress> SERVER_MACS = new HashMap<>();

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
    }

    // Load balancing flows
    static void addIncomingLoadBalancing(IOFSwitch ofSwitch, TableId tableId, IPv4AddressWithMask prefix, IPv4Address vip, IPv4Address dip) {
        OFFactory factory = ofSwitch.getOFFactory();
        OFInstructions instructions = factory.instructions();

        Match match = buildIncomingMatch(factory, vip, prefix);

        List<OFAction> actionList = buildIncomingActionList(factory, dip);

        List<OFInstruction> instructionList = Arrays.asList(
            instructions.applyActions(actionList),
            instructions.gotoTable(TableId.of(tableId.getValue() + 1)));

        // TODO Transitions?

        ofSwitch.write(factory
            .buildFlowAdd()
            .setTableId(tableId)
            .setMatch(match)
            .setInstructions(instructionList)
            .build());
    }

    static void deleteStrictIncomingLoadBalancing(IOFSwitch ofSwitch, TableId tableId, IPv4AddressWithMask prefix, IPv4Address vip, IPv4Address dip) {
        OFFactory factory = ofSwitch.getOFFactory();
        OFInstructions instructions = factory.instructions();

        Match match = buildIncomingMatch(factory, vip, prefix);

        List<OFAction> actionList = buildIncomingActionList(factory, dip);

        List<OFInstruction> instructionList = Arrays.asList(
            instructions.applyActions(actionList),
            instructions.gotoTable(TableId.of(tableId.getValue() + 1)));

        // TODO Transitions?

        ofSwitch.write(factory
            .buildFlowDeleteStrict()
            .setTableId(tableId)
            .setMatch(match)
            .setInstructions(instructionList)
            .build());
    }

    private static Match buildIncomingMatch(OFFactory factory, IPv4Address vip, IPv4AddressWithMask prefix) {
        return factory
            .buildMatch()
            .setExact(MatchField.ETH_TYPE, EthType.IPv4)
            .setExact(MatchField.IPV4_DST, vip)
            .setMasked(MatchField.IPV4_SRC, prefix)
            .build();
    }

    private static List<OFAction> buildIncomingActionList(OFFactory factory, IPv4Address dip) {
        OFActions actions = factory.actions();
        switch (factory.getVersion()) {
            case OF_13:
                OFOxms oxms = factory.oxms();
                return Arrays.asList(
                    actions.setField(oxms.ethSrc(SWITCH_MAC)),
                    actions.setField(oxms.ethDst(SERVER_MACS.get(dip))),
                    actions.setField(oxms.ipv4Dst(dip)));
            default:
                throw new UnsupportedOperationException(factory.getVersion().toString() + " not supported");
        }
    }

    // Traffic measurement flows
    static void addIncomingTrafficMeasurement(IOFSwitch ofSwitch, TableId tableId, IPv4AddressWithMask prefix, IPv4Address vip) {
        OFFactory factory = ofSwitch.getOFFactory();
        OFInstructions instructions = factory.instructions();

        // Build match
        Match match = factory
            .buildMatch()
            .setExact(MatchField.ETH_TYPE, EthType.IPv4)
            .setMasked(MatchField.IPV4_SRC, prefix)
            .setExact(MatchField.IPV4_DST, vip)
            .build();

        // Build instructions
        List<OFInstruction> instructionList = Collections
            .singletonList(instructions.gotoTable(TableId.of(tableId.getValue() + 1)));

        // TODO skip relevant flows for inc/out

        ofSwitch.write(factory
            .buildFlowAdd()
            .setTableId(tableId)
            .setMatch(match)
            .setInstructions(instructionList)
            .build());
    }

    static void addOutgoingTrafficMeasurement(IOFSwitch ofSwitch, TableId tableId, IPv4AddressWithMask prefix, IPv4Address vip) {
        OFFactory factory = ofSwitch.getOFFactory();
        OFInstructions instructions = factory.instructions();

        // Build match
        Match match = factory
            .buildMatch()
            .setExact(MatchField.ETH_TYPE, EthType.IPv4)
            .setMasked(MatchField.IPV4_DST, prefix)
            .setExact(MatchField.IPV4_SRC, vip)
            .build();

        // Build instructions
        List<OFInstruction> instructionList = Collections
            .singletonList(instructions.gotoTable(TableId.of(tableId.getValue() + 1)));

        // TODO skip relevant flows for inc/out

        ofSwitch.write(factory
            .buildFlowAdd()
            .setTableId(tableId)
            .setMatch(match)
            .setInstructions(instructionList)
            .build());
    }
}
