package net.floodlightcontroller.proactiveloadbalancer.web;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.io.IOException;

class DatapathIdKeyDeserializer extends StdKeyDeserializer {

    DatapathIdKeyDeserializer() {
        super(-1, IPv4AddressWithMask.class);
    }

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        return DatapathId.of(key);
    }
}
