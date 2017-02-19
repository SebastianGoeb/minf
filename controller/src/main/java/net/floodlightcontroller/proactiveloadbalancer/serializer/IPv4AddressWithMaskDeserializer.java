package net.floodlightcontroller.proactiveloadbalancer.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.io.IOException;

public class IPv4AddressWithMaskDeserializer extends StdDeserializer<IPv4AddressWithMask> {

	public IPv4AddressWithMaskDeserializer() {
		super(IPv4Address.class);
	}

	@Override
	public IPv4AddressWithMask deserialize(JsonParser parser, DeserializationContext context)
			throws IOException, JsonProcessingException {
		JsonNode node = parser.getCodec().readTree(parser);
		String ipString = node.asText();
		return IPv4AddressWithMask.of(ipString);
	}
}
