package net.floodlightcontroller.proactiveloadbalancer.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.projectfloodlight.openflow.types.DatapathId;

import java.io.IOException;

class DatapathIdDeserializer extends StdDeserializer<DatapathId> {

    private static final long serialVersionUID = 1L;

    DatapathIdDeserializer() {
        super(DatapathId.class);
    }

    @Override
    public DatapathId deserialize(JsonParser parser, DeserializationContext context)
            throws IOException, JsonProcessingException {
        JsonNode node = parser.getCodec().readTree(parser);
        String ipString = node.asText();
        return DatapathId.of(ipString);
    }
}
