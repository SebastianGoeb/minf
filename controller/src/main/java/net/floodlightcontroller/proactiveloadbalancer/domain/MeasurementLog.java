package net.floodlightcontroller.proactiveloadbalancer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.floodlightcontroller.proactiveloadbalancer.serializer.DatapathIdKeyDeserializer;
import net.floodlightcontroller.proactiveloadbalancer.serializer.IPv4AddressKeyDeserializer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.HashMap;

public class MeasurementLog {

    private class Rules {
        private long measurement;
        private long prefix;
        private long connection;

        public Rules(long measurement, long prefix, long connection) {
            this.measurement = measurement;
            this.prefix = prefix;
            this.connection = connection;
        }
    }

    @JsonProperty
    private long millis;

    @JsonProperty
    @JsonSerialize(keyUsing = ToStringSerializer.class)
    @JsonDeserialize(keyUsing = IPv4AddressKeyDeserializer.class)
    private HashMap<IPv4Address, Long> bytes;

    @JsonProperty
    @JsonSerialize(keyUsing = ToStringSerializer.class)
    @JsonDeserialize(keyUsing = DatapathIdKeyDeserializer.class)
    private HashMap<DatapathId, Rules> rules;

    public MeasurementLog(long millis,
            HashMap<IPv4Address, Long> bytes,
            HashMap<DatapathId, Rules> rules) {
        this.millis = millis;
        this.bytes = bytes;
        this.rules = rules;
    }

    public long getMillis() {
        return millis;
    }

    public HashMap<IPv4Address, Long> getBytes() {
        return bytes;
    }

    public HashMap<DatapathId, Rules> getRules() {
        return rules;
    }
}
