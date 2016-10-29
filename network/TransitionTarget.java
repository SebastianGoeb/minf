package net.floodlightcontroller.serverloadbalancer.network;

public class TransitionTarget implements ForwardingTarget {
    private ForwardingTarget oldTarget;
    private ForwardingTarget newTarget;

    public TransitionTarget(ForwardingTarget oldTarget, ForwardingTarget newTarget) {
        this.oldTarget = oldTarget;
        this.newTarget = newTarget;
    }

    public ForwardingTarget getOldTarget() {
        return oldTarget;
    }

    public ForwardingTarget getNewTarget() {
        return newTarget;
    }

    @Override
    public boolean equals(Object obj) {
        return (this == obj)
                || (obj instanceof TransitionTarget
                && oldTarget.equals(((TransitionTarget) obj).oldTarget)
                && newTarget.equals(((TransitionTarget) obj).newTarget));
    }

    @Override
    public String toString() {
        return String.format("(%s -> %s)", oldTarget, newTarget);
    }
}
