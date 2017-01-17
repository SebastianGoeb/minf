package net.floodlightcontroller.proactiveloadbalancer.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TableId;

public class FlowBuilder {

	private static final MacAddress SWITCH_MAC = MacAddress.of("00:00:02:02:02:02");
	private static final Map<IPv4Address, MacAddress> SERVER_MACS = new HashMap<>(); // TODO might not need this if ToR swiches do this as part of forwarding

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

	public static Match buildIncomingMatch(OFFactory factory, IPv4Address vip, IPv4AddressWithMask prefix) {
		Match.Builder builder = factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_DST, vip);

		if (prefix.getMask() == IPv4Address.NO_MASK) { // /32
			builder.setExact(MatchField.IPV4_SRC, prefix.getValue());
		} else if (prefix.getMask() != IPv4Address.FULL_MASK) { // /1-31
			builder.setMasked(MatchField.IPV4_SRC, prefix);
		}

		return builder.build();
	}

	public static List<OFAction> buildIncomingActionList(OFFactory factory, IPv4Address dip) {
		ArrayList<OFAction> actionList = new ArrayList<>();
		OFActions actions = factory.actions();

		switch(factory.getVersion()) {
		case OF_10:
			// Set source MAC
			actionList.add(actions.setDlSrc(SWITCH_MAC));

			// Set destination MAC
			// TODO might not need this if ToR swiches do this as part of forwarding
			actionList.add(actions.setDlDst(SERVER_MACS.get(dip)));

			// Set destination IP
			actionList.add(actions.setNwDst(dip));
			break;
		case OF_13:
			OFOxms oxms = factory.oxms();
			
			// Set source MAC
			actionList.add(actions.setField(oxms.ethSrc(SWITCH_MAC)));

			// Set destination MAC
			// TODO might not need this if ToR swiches do this as part of forwarding
			actionList.add(actions.setField(oxms.ethDst(SERVER_MACS.get(dip))));

			// Set destination IP
			actionList.add(actions.setField(oxms.ipv4Dst(dip)));
			break;
		default:
			throw new UnsupportedOperationException(factory.getVersion().toString() + " not supported");
		}

		return actionList;
	}

	public static List<OFInstruction> buildIncomingInstructionList(OFFactory factory, List<OFAction> actionList,
			DatapathId dpid) {
		OFInstructions instructions = factory.instructions();
		ArrayList<OFInstruction> instructionList = new ArrayList<>();

		// Apply actions
		instructionList.add(instructions.buildApplyActions().setActions(actionList).build());

		// Goto forwarding table
		int baseTableId = (int) ((dpid.getLong() - 1) * 5 + 1);
		int fwdTableId = baseTableId + 1;
		instructionList.add(instructions.buildGotoTable().setTableId(TableId.of(fwdTableId)).build());

		return instructionList;
	}
}
