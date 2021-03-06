package net.floodlightcontroller.proactiveloadbalancer.domain;

import org.projectfloodlight.openflow.types.U64;

public enum Strategy {
    uniform, non_uniform, round_robin, ip_hash, least_conn;

    public static final int COOKIE_BASE = 100;

    public U64 cookie() {
        return U64.of(COOKIE_BASE + ordinal());
    }

    public boolean isPrefixBased() {
        return this == uniform || this == non_uniform;
    }
}
