package net.floodlightcontroller.serverloadbalancer;

import com.fasterxml.jackson.annotation.JsonGetter;
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
    private static int nextId = 0;

    protected static Logger logger = LoggerFactory.getLogger(Server.class);

    @JsonProperty("ip")
    private IPv4Address nwAddress;

    @JsonProperty("mac")
    private MacAddress dlAddress;

    @JsonProperty("port")
    private String port;

    @JsonProperty("weight")
    private double weight;

    @JsonProperty("id")
    private int id;

    private boolean safeToDelete;

    private Server() {
        this.id = nextId++;
        this.safeToDelete = false;
    }

    public static Server create(IPv4Address nwAddress, MacAddress dlAddress, String port, double weight) {
        return new Server()
                .setNwAddress(nwAddress)
                .setDlAddress(dlAddress)
                .setPort(port)
                .setWeight(weight)
                .setId(nextId++);
    }

    public static Server create(String nwAddress, String dlAddress, String port, double weight) {
        return create(
                IPv4Address.of(nwAddress),
                MacAddress.of(dlAddress),
                port,
                weight);
    }

    public static Server fromJson(String fmJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(fmJson, Server.class);
    }

    public static List<Server> fromJsonList(String fmJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(fmJson, new TypeReference<List<Server>>() {});
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

    public String getPort() {
        return port;
    }

    public Server setPort(String port) {
        this.port = port;
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

    public Server setId(int id) {
        this.id = id;
        return this;
    }

    public boolean isSafeToDelete() {
        return safeToDelete;
    }

    public Server setSafeToDelete(boolean safeToDelete) {
        this.safeToDelete = safeToDelete;
        return this;
    }

    // JSON properties

    @JsonSetter("ip")
    public Server setJsonNwAddress(String s) {
        return this.setNwAddress(IPv4Address.of(s));
    }

    @JsonGetter("ip")
    public String getJsonNwAddress() {
        return this.getNwAddress().toString();
    }

    @JsonSetter("mac")
    public Server setJsonDlAddress(String s) {
        return this.setDlAddress(MacAddress.of(s));
    }

    @JsonGetter("mac")
    public String getJsonDlAddress() {
        return this.getDlAddress().toString();
    }

    @Override
    public String toString() {
        return String.format("Server %d IP %s MAC %s port %s weight %.2f", this.id, this.nwAddress, this.dlAddress, this.port, this.weight);
    }
}
