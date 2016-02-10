package net.floodlightcontroller.serverloadbalancer;

public abstract class LoadBalanceTarget {
    private int weight;

    public LoadBalanceTarget() {
        this(0);
    }

    public LoadBalanceTarget(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public LoadBalanceTarget setWeight(int weight) {
        this.weight = weight;
        return this;
    }
}
