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

    public String getIntf() {
        return intf;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getLocalSubnet() {
        return localSubnet;
    }

    public int getClients() {
        return clients;
    }

    public String getRate() {
        return rate;
    }

    public String getSize() {
        return size;
    }

    public Distribution getLocalAddressDistribution() {
        return localAddressDistribution;
    }

    public int getDuration() {
        return duration;
    }
}
