package com.sebastiangoeb.minf.driver;

import com.google.gson.annotations.SerializedName;

class Traffic {

    private String intf;
    private String remoteAddress;
    private String localSubnet;
    private int clients;
    private String rate;
    private String size;
    @SerializedName("localAddress")
    private CompositeDistribution localAddressDistribution;
    private int duration;
    private int cycle;

    @SuppressWarnings("unused")
    public Traffic(String intf,
            String remoteAddress,
            String localSubnet,
            int clients,
            String rate,
            String size,
            CompositeDistribution localAddressDistribution,
            int duration) {
        this(intf,
                remoteAddress,
                localSubnet,
                clients,
                rate,
                size,
                localAddressDistribution,
                duration,
                0);
    }

    @SuppressWarnings("WeakerAccess")
    public Traffic(String intf,
            String remoteAddress,
            String localSubnet,
            int clients,
            String rate,
            String size,
            CompositeDistribution localAddressDistribution,
            int duration,
            int cycle) {
        this.intf = intf;
        this.remoteAddress = remoteAddress;
        this.localSubnet = localSubnet;
        this.clients = clients;
        this.rate = rate;
        this.size = size;
        this.localAddressDistribution = localAddressDistribution;
        this.duration = duration;
        this.cycle = cycle;
    }

    String getIntf() {
        return intf;
    }

    String getRemoteAddress() {
        return remoteAddress;
    }

    String getLocalSubnet() {
        return localSubnet;
    }

    int getClients() {
        return clients;
    }

    String getRate() {
        return rate;
    }

    String getSize() {
        return size;
    }

    CompositeDistribution getLocalAddressDistribution() {
        return localAddressDistribution;
    }

    int getDuration() {
        return duration;
    }

    public int getCycle() {
        return cycle;
    }

    @Override
    public String toString() {
        return "Clients:  " + clients
                + "\nDist:     " + localAddressDistribution
                + "\nDuration: " + duration + " s"
                + "\nCycle: " + cycle + " s";
    }
}
