package net.floodlightcontroller.proactiveloadbalancer.network;

public interface ForwardingTarget {
    ForwardingTarget NONE = new ForwardingTarget() {
        @Override
        public String toString() {
            return "NONE";
        }
    };
}
