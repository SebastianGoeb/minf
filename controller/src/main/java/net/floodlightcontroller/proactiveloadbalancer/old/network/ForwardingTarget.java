package net.floodlightcontroller.proactiveloadbalancer.old.network;

public interface ForwardingTarget {
    ForwardingTarget NONE = new ForwardingTarget() {
        @Override
        public String toString() {
            return "NONE";
        }
    };
}
