package net.floodlightcontroller.serverloadbalancer;

import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Transition {
    List<AssignmentWithMask> from;
    List<AssignmentWithMask> to;

    public Transition(List<AssignmentWithMask> from, List<AssignmentWithMask> to) {
        this.from = from;
        this.to = to;
    }

    // Factories
    public static Transition direct(Integer fromServer, IPv4AddressWithMask fromPrefix,
                                    Integer toServer, IPv4AddressWithMask toPrefix) {
        List<AssignmentWithMask> from = Collections.singletonList(new AssignmentWithMask(fromPrefix, fromServer));
        List<AssignmentWithMask> to = Collections.singletonList(new AssignmentWithMask(toPrefix, toServer));
        return new Transition(from, to);
    }

    public static Transition merge(Integer toServer, IPv4AddressWithMask toPrefix) {
        List<AssignmentWithMask> from = new ArrayList<>();
        List<AssignmentWithMask> to = Collections.singletonList(new AssignmentWithMask(toPrefix, toServer));
        return new Transition(from, to);
    }

    public static Transition split(Integer fromServer, IPv4AddressWithMask fromPrefix) {
        List<AssignmentWithMask> from = Collections.singletonList(new AssignmentWithMask(fromPrefix, fromServer));
        List<AssignmentWithMask> to = new ArrayList<>();
        return new Transition(from, to);
    }

    public void addFrom(Integer server, IPv4AddressWithMask prefix) {
        from.add(new AssignmentWithMask(prefix, server));
    }

    public void addTo(Integer server, IPv4AddressWithMask prefix) {
        to.add(new AssignmentWithMask(prefix, server));
    }

    public List<AssignmentWithMask> getFrom() {
        return from;
    }

    public List<AssignmentWithMask> getTo() {
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
