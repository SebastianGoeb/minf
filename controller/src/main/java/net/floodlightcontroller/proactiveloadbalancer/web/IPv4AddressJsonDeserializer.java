package net.floodlightcontroller.proactiveloadbalancer.web;

import java.io.IOException;

import org.projectfloodlight.openflow.types.IPv4Address;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class IPv4AddressJsonDeserializer extends StdDeserializer<IPv4Address> {

	private static final long serialVersionUID = 1L;

	public IPv4AddressJsonDeserializer(Class<IPv4Address> t) {
		super(t);
	}

	@Override
	public IPv4Address deserialize(JsonParser parser, DeserializationContext context)
			throws IOException, JsonProcessingException {
		JsonNode node = parser.getCodec().readTree(parser);
		String ipString = node.asText();
		return IPv4Address.of(ipString);
	}
}
