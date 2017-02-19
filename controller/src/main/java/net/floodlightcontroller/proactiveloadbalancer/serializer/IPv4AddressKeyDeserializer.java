package net.floodlightcontroller.proactiveloadbalancer.serializer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.io.IOException;

public class IPv4AddressKeyDeserializer extends StdKeyDeserializer {

    public IPv4AddressKeyDeserializer() {
        super(-1, IPv4Address.class);
    }

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        return IPv4Address.of(key);
    }
}
