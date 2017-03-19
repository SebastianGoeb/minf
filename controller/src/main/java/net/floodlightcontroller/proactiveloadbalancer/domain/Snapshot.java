package net.floodlightcontroller.proactiveloadbalancer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializer;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public class Snapshot {

    @JsonProperty
    private long timestamp;

    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    private Map<DatapathId, Integer> numRules;

    @JsonProperty
    @JsonSerialize(keyUsing = StdKeySerializer.class)
    private Map<DatapathId, List<Measurement>> clientMeasurements;

    @JsonProperty
    private List<Measurement> serverMeasurements;

    private Snapshot(long timestamp,
            Map<DatapathId, Integer> numRules,
            Map<DatapathId, List<Measurement>> clientMeasurements,
            List<Measurement> serverMeasurements) {
        this.timestamp = timestamp;
        this.numRules = numRules;
        this.clientMeasurements = clientMeasurements;
        this.serverMeasurements = serverMeasurements;
    }

    public Snapshot() {
        this(-1, emptyMap(), emptyMap(), emptyList());
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

    public Map<DatapathId, List<Measurement>> getClientMeasurements() {
        return clientMeasurements;
    }

    public Snapshot setClientMeasurements(Map<DatapathId, List<Measurement>> clientMeasurements) {
        this.clientMeasurements = clientMeasurements;
        return this;
    }

    public List<Measurement> getServerMeasurements() {
        return serverMeasurements;
    }

    public Snapshot setServerMeasurements(List<Measurement> serverMeasurements) {
        this.serverMeasurements = serverMeasurements;
        return this;
    }

    public String toJson() {
        try {
            String json = new ObjectMapper().writeValueAsString(this);
            return json.replaceAll("\\n", ""); // Just in case
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
