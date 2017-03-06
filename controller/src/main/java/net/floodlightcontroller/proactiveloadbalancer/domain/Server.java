package net.floodlightcontroller.proactiveloadbalancer.domain;

import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Objects;

public class Server {
    private IPv4Address dip;
    private double weight;

    public Server(IPv4Address dip, double weight) {
        this.dip = dip;
        this.weight = weight;
    }

    public IPv4Address getDip() {
        return dip;
    }

    public Server setDip(IPv4Address dip) {
        this.dip = dip;
        return this;
    }

    public double getWeight() {
        return weight;
    }

    public Server setWeight(double weight) {
        this.weight = weight;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return Double.compare(server.weight, weight) == 0 &&
                Objects.equals(dip, server.dip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dip, weight);
    }

    @Override
    public String toString() {
        return "{" + dip + ": " + weight + '}';
    }
}
