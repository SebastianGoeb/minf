package net.floodlightcontroller.proactiveloadbalancer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Map;

public class Snapshot {

    @JsonProperty
    private long timestamp;

    @JsonProperty
    @JsonSerialize(keyUsing = ToStringSerializer.class)
    private Map<DatapathId, Integer> numRules;

    public Snapshot(long timestamp, Map<DatapathId, Integer> numRules) {
        this.timestamp = timestamp;
        this.numRules = numRules;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Snapshot setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public Map<DatapathId, Integer> getNumRules() {
        return numRules;
    }

    public Snapshot setNumRules(Map<DatapathId, Integer> numRules) {
        this.numRules = numRules;
        return this;
    }

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this).replaceAll("\\n", "");
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
