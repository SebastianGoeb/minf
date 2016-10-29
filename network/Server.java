package net.floodlightcontroller.serverloadbalancer.network;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class Server extends LoadBalanceTarget {
    protected static Logger logger = LoggerFactory.getLogger(Server.class);
    private static int nextId = 0;

    @JsonProperty("ip")
    private IPv4Address nwAddress;

    @JsonProperty("mac")
    private MacAddress dlAddress;

    @JsonProperty("weight")
    private double weight;

    @JsonIgnore
    private boolean safeToDelete;

    private Server() {
        super();
        this.safeToDelete = false;
    }

    public static Server create(IPv4Address nwAddress, MacAddress dlAddress, double weight) {
        return new Server()
                .setNwAddress(nwAddress)
                .setDlAddress(dlAddress)
                .setWeight(weight);
    }

    public static Server create(String nwAddress, String dlAddress, double weight) {
        return create(
                IPv4Address.of(nwAddress),
                MacAddress.of(dlAddress),
                weight);
    }

    public static Server fromJson(String fmJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(fmJson, Server.class);
    }

    public static List<Server> fromJsonList(String fmJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(fmJson, new TypeReference<List<Server>>() {
        });
    }

    // Regular properties

    public IPv4Address getNwAddress() {
        return nwAddress;
    }

    public Server setNwAddress(IPv4Address nwAddress) {
        this.nwAddress = nwAddress;
        return this;
    }

    public MacAddress getDlAddress() {
        return dlAddress;
    }

    public Server setDlAddress(MacAddress dlAddress) {
        this.dlAddress = dlAddress;
        return this;
    }

    public double getWeight() {
        return weight;
    }

    public Server setWeight(double weight) {
        this.weight = weight;
        return this;
    }

    public int getId() {
        return id;
    }

    public boolean isSafeToDelete() {
        return safeToDelete;
    }

    public Server setSafeToDelete(boolean safeToDelete) {
        this.safeToDelete = safeToDelete;
        return this;
    }

    // JSON properties

    @JsonGetter("ip")
    public String getJsonNwAddress() {
        return this.getNwAddress().toString();
    }

    @JsonSetter("ip")
    public Server setJsonNwAddress(String s) {
        return this.setNwAddress(IPv4Address.of(s));
    }

    @JsonGetter("mac")
    public String getJsonDlAddress() {
        return this.getDlAddress().toString();
    }

    @JsonSetter("mac")
    public Server setJsonDlAddress(String s) {
        return this.setDlAddress(MacAddress.of(s));
    }

    @Override
    public String toString() {
        return String.format("Server %d IP %s weight %.2f", this.id, this.nwAddress, this.weight);
    }

    @Override
    public boolean equals(Object obj) {
        return (this == obj)
                || (obj instanceof Server
                && id == ((Server) obj).id);
    }
}
