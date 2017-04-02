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

    IPv4AddressWithMask RANGE = IPv4AddressWithMask.of("10.0.0.0/8");
    List<LoadBalancingFlow> DEFAULT= singletonList(new LoadBalancingFlow(RANGE, null));

    @Test
    public void assignPrefixes_whenEmptyServers_returnsDefault() {
        List<Server> servers = emptyList();

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, null, servers);

        assertThat(result, equalTo(DEFAULT));
    }

    @Test
    public void assignPrefixes_whenSingleServer_assignsAllPrefixesToSingleServer() {
        List<WeightedPrefix> measurements = singletonList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1));
        List<Server> servers = singletonList(
                new Server(IPv4Address.of("10.0.0.1"), 1));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, measurements, servers);

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

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, measurements, servers);

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

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, measurements, servers);

        List<LoadBalancingFlow> expectedResult = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void assignPrefixes_whenMultipleServers_splitPrefixesByWeight3() {
        List<WeightedPrefix> measurements = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 0.3),
                new WeightedPrefix(IPv4AddressWithMask.of("11.0.0.0/8"), 0.3),
                new WeightedPrefix(IPv4AddressWithMask.of("12.0.0.0/8"), 0.4));
        List<Server> servers = asList(
                new Server(IPv4Address.of("10.0.0.1"), 1),
                new Server(IPv4Address.of("10.0.0.2"), 1));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, measurements, servers);

        List<LoadBalancingFlow> expectedResult = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("12.0.0.0/8"), IPv4Address.of("10.0.0.2")));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void assignPrefixes_whenSomeMeasurementsZero_splitPrefixesByWeight() {
        List<WeightedPrefix> measurements = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 0),
                new WeightedPrefix(IPv4AddressWithMask.of("11.0.0.0/8"), 1));
        List<Server> servers = asList(
                new Server(IPv4Address.of("10.0.0.1"), 1),
                new Server(IPv4Address.of("10.0.0.2"), 1));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, measurements, servers);

        List<LoadBalancingFlow> expectedResult = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void assignPrefixes_whenSomeMeasurementsZero_splitPrefixesByWeight2() {
        List<WeightedPrefix> measurements = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/10"), 0),
                new WeightedPrefix(IPv4AddressWithMask.of("10.64.0.0/10"), 0),
                new WeightedPrefix(IPv4AddressWithMask.of("10.128.0.0/10"), 0),
                new WeightedPrefix(IPv4AddressWithMask.of("10.192.0.0/10"), 1));
        List<Server> servers = asList(
                new Server(IPv4Address.of("10.0.0.2"), 2),
                new Server(IPv4Address.of("10.0.0.3"), 1),
                new Server(IPv4Address.of("10.0.0.4"), 1));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, measurements, servers);

        List<LoadBalancingFlow> expectedResult = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/10"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.64.0.0/10"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.128.0.0/10"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.192.0.0/10"), IPv4Address.of("10.0.0.2")));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void assignPrefixes_whenLastMeasurementsZero_splitPrefixesByWeight() {
        List<WeightedPrefix> measurements = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/10"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.64.0.0/10"), 0),
                new WeightedPrefix(IPv4AddressWithMask.of("10.128.0.0/10"), 0),
                new WeightedPrefix(IPv4AddressWithMask.of("10.192.0.0/10"), 0));
        List<Server> servers = asList(
                new Server(IPv4Address.of("10.0.0.2"), 2),
                new Server(IPv4Address.of("10.0.0.3"), 1),
                new Server(IPv4Address.of("10.0.0.4"), 1));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, measurements, servers);

        List<LoadBalancingFlow> expectedResult = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/10"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.64.0.0/10"), IPv4Address.of("10.0.0.4")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.128.0.0/10"), IPv4Address.of("10.0.0.4")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.192.0.0/10"), IPv4Address.of("10.0.0.4")));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void assignPrefixes_whenLastMeasurementsZero_splitPrefixesByWeight2() {
        /*
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.0.0.0/14: 164622B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.4.0.0/15: 66396B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.6.0.0/15: 140458B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.8.0.0/14: 279327B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.12.0.0/15: 138826B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.14.0.0/15: 216483B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.16.0.0/15: 210740B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.18.0.0/16: 62714B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.19.0.0/16: 111144B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.20.0.0/15: 261702B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.22.0.0/15: 175113B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.24.0.0/15: 205442B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.26.0.0/15: 278373B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.28.0.0/15: 265866B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.30.0.0/15: 137579B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.32.0.0/15: 306957B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.34.0.0/15: 210024B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.36.0.0/15: 158141B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.38.0.0/15: 156742B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.40.0.0/15: 153436B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.42.0.0/15: 158474B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.44.0.0/14: 240458B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.48.0.0/13: 307170B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.56.0.0/13: 103625B}
        2017-03-27 21:58:17.642 INFO  [n.f.p.ProactiveLoadBalancer] New measurement: {10.64.0.0/10: 32369B}
        */
        List<WeightedPrefix> measurements = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/14"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.4.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.6.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.8.0.0/14"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.12.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.14.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.16.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.18.0.0/16"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.19.0.0/16"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.20.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.22.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.24.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.26.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.28.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.30.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.32.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.34.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.36.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.38.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.40.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.42.0.0/15"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.44.0.0/14"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.48.0.0/13"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.56.0.0/13"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.64.0.0/10"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.128.0.0/9"), 0));
        List<Server> servers = asList(
                new Server(IPv4Address.of("10.0.0.1"), 1),
                new Server(IPv4Address.of("10.0.0.2"), 1));

        List<LoadBalancingFlow> result = GreedyPrefixAssigner.assignPrefixes(RANGE, measurements, servers);

        List<LoadBalancingFlow> expectedResult = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/14"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.4.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.6.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.8.0.0/14"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.12.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.14.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.16.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.18.0.0/16"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.19.0.0/16"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.20.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.22.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.24.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.26.0.0/15"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.28.0.0/15"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.30.0.0/15"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.32.0.0/15"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.34.0.0/15"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.36.0.0/15"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.38.0.0/15"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.40.0.0/15"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.42.0.0/15"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.44.0.0/14"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.48.0.0/13"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.56.0.0/13"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.64.0.0/10"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.128.0.0/9"), IPv4Address.of("10.0.0.2")));
        assertThat(result, equalTo(expectedResult));
    }
}