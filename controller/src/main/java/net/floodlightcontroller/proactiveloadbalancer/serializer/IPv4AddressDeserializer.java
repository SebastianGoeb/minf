package net.floodlightcontroller.proactiveloadbalancer.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.io.IOException;

public class IPv4AddressDeserializer extends StdDeserializer<IPv4Address> {

	public IPv4AddressDeserializer() {
		super(IPv4Address.class);
	}

	@Override
	public IPv4Address deserialize(JsonParser parser, DeserializationContext context)
			throws IOException, JsonProcessingException {
		JsonNode node = parser.getCodec().readTree(parser);
		String ipString = node.asText();
		return IPv4Address.of(ipString);
	}
}
