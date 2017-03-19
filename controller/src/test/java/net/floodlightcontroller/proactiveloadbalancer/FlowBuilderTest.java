package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.LoadBalancingFlow;
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

public class FlowBuilderTest extends FloodlightTestCase {

    // Tests merge contiguous flows
    @Test
    public void merge_whenEmpty_returnsEmpty() {
        List<LoadBalancingFlow> flows = emptyList();

        List<LoadBalancingFlow> result = FlowBuilder.mergeContiguousFlows(flows);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void merge_whenSingleFlow_returnsThatFlow() {
        List<LoadBalancingFlow> flows = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        List<LoadBalancingFlow> result = FlowBuilder.mergeContiguousFlows(flows);

        assertThat(result, equalTo(singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")))));
    }

    @Test
    public void merge_whenContiguousFlows_returnsSuperFlow() {
        List<LoadBalancingFlow> flows = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        List<LoadBalancingFlow> result = FlowBuilder.mergeContiguousFlows(flows);

        assertThat(result, equalTo(singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/7"), IPv4Address.of("10.0.0.1")))));
    }

    @Test
    public void merge_whenNoncontiguousFlows_returnsIndividualFlows() {
        List<LoadBalancingFlow> flows = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("12.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        List<LoadBalancingFlow> result = FlowBuilder.mergeContiguousFlows(flows);

        assertThat(result, equalTo(asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("12.0.0.0/8"), IPv4Address.of("10.0.0.1")))));
    }

    @Test
    public void merge_whenContiguousFlowsButDifferentIPs_returnsIndividualFlows() {
        List<LoadBalancingFlow> flows = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.2")));

        List<LoadBalancingFlow> result = FlowBuilder.mergeContiguousFlows(flows);

        assertThat(result, equalTo(asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.2")))));
    }
}
