package net.floodlightcontroller.proactiveloadbalancer.serializer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.io.IOException;

public class IPv4AddressWithMaskKeyDeserializer extends StdKeyDeserializer {

    public IPv4AddressWithMaskKeyDeserializer() {
        super(-1, IPv4AddressWithMask.class);
    }

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        return IPv4AddressWithMask.of(key);
    }
}
