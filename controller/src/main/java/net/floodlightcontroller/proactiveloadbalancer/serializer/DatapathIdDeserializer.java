package net.floodlightcontroller.proactiveloadbalancer.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.projectfloodlight.openflow.types.DatapathId;

import java.io.IOException;

public class DatapathIdDeserializer extends StdDeserializer<DatapathId> {

    public DatapathIdDeserializer() {
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
