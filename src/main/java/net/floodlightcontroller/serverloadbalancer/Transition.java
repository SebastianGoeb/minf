package net.floodlightcontroller.serverloadbalancer;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.*;

public class Transition {
    List<Assignment> from;
    List<Assignment> to;

    public Transition(List<Assignment> from, List<Assignment> to) {
        this.from = from;
        this.to = to;
    }

    // Factories
    public static Transition direct(Integer fromServer, IPv4AddressWithMask fromPrefix,
                                    Integer toServer, IPv4AddressWithMask toPrefix) {
        List<Assignment> from = Collections.singletonList(new Assignment(fromPrefix, fromServer));
        List<Assignment> to = Collections.singletonList(new Assignment(toPrefix, toServer));
        return new Transition(from, to);
    }

    public static Transition merge(Integer toServer, IPv4AddressWithMask toPrefix) {
        List<Assignment> from = new ArrayList<>();
        List<Assignment> to = Collections.singletonList(new Assignment(toPrefix, toServer));
        return new Transition(from, to);
    }

    public static Transition split(Integer fromServer, IPv4AddressWithMask fromPrefix) {
        List<Assignment> from = Collections.singletonList(new Assignment(fromPrefix, fromServer));
        List<Assignment> to = new ArrayList<>();
        return new Transition(from, to);
    }

    public void addFrom(Integer server, IPv4AddressWithMask prefix) {
        from.add(new Assignment(prefix, server));
    }

    public void addTo(Integer server, IPv4AddressWithMask prefix) {
        to.add(new Assignment(prefix, server));
    }

    public List<Assignment> getFrom() {
        return from;
    }

    public List<Assignment> getTo() {
        return to;
    }

    public boolean isDirect() {
        return from.size() == 1 && to.size() == 1;
    }

    public boolean isSplit() {
        return from.size() == 1 && to.size() > 1;
    }

    public boolean isMerge() {
        return from.size() > 1 && to.size() == 1;
    }

    public IPv4AddressWithMask prefix() {
        if (isMerge()) {
            return to.get(0).getPrefix();
        } else {
            return from.get(0).getPrefix();
        }
    }
}
