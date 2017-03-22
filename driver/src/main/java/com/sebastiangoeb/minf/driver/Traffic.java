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
    private Distribution localAddressDistribution;
    private int duration;

    public Traffic(String intf,
            String remoteAddress,
            String localSubnet,
            int clients,
            String rate,
            String size,
            Distribution localAddressDistribution, int duration) {
        this.intf = intf;
        this.remoteAddress = remoteAddress;
        this.localSubnet = localSubnet;
        this.clients = clients;
        this.rate = rate;
        this.size = size;
        this.localAddressDistribution = localAddressDistribution;
        this.duration = duration;
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

    Distribution getLocalAddressDistribution() {
        return localAddressDistribution;
    }

    int getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return "Clients:  " + clients
                + "\nDist:     " + localAddressDistribution
                + "\nDuration: " + duration + " s";
    }
}
