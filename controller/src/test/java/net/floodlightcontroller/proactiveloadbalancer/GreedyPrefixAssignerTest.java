package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.LoadBalancingFlow;
import net.floodlightcontroller.proactiveloadbalancer.domain.Server;
import net.floodlightcontroller.proactiveloadbalancer.domain.WeightedPrefix;
import net.floodlightcontroller.test.FloodlightTestCase;
import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class GreedyPrefixAssignerTest extends FloodlightTestCase {

    @Test
    public void assignPrefixes_whenEmptyServers_returnsDefault() {
        List<Server> servers = emptyList();

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(null, servers);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void assignPrefixes_whenSingleServer_assignsAllPrefixesToSingleServer() {
        List<WeightedPrefix> measurements = singletonList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1));
        List<Server> servers = singletonList(
                new Server(IPv4Address.of("10.0.0.1"), 1));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(measurements, servers);

        List<LoadBalancingFlow> expectedResult = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void assignPrefixes_whenMultipleServers_splitPrefixesByWeight1() {
        List<WeightedPrefix> measurements = singletonList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1));
        List<Server> servers = asList(
                new Server(IPv4Address.of("10.0.0.1"), 1),
                new Server(IPv4Address.of("10.0.0.2"), 2));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(measurements, servers);

        List<LoadBalancingFlow> expectedResult = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.2")));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void assignPrefixes_whenMultipleServers_splitPrefixesByWeight2() {
        List<WeightedPrefix> measurements = singletonList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1));
        List<Server> servers = asList(
                new Server(IPv4Address.of("10.0.0.1"), 2),
                new Server(IPv4Address.of("10.0.0.2"), 1));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(measurements, servers);

        List<LoadBalancingFlow> expectedResult = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        assertThat(result, equalTo(expectedResult));
    }
}