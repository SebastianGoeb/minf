package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.LoadBalancingFlow;
import net.floodlightcontroller.proactiveloadbalancer.domain.Transition;
import net.floodlightcontroller.test.FloodlightTestCase;
import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DifferenceFinderTest extends FloodlightTestCase {

    // Tests for contiguous prefixes
    @Test
    public void prefixesAreContiguous_whenEmpty_returnsTrue() {
        List<LoadBalancingFlow> flows = emptyList();

        boolean result = DifferenceFinder.prefixesAreContiguous(flows);

        assertThat(result, is(true));
    }

    @Test
    public void prefixesAreContiguous_whenSingle_returnsTrue() {
        List<LoadBalancingFlow> flows = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        boolean result = DifferenceFinder.prefixesAreContiguous(flows);

        assertThat(result, is(true));
    }

    @Test
    public void prefixesAreContiguous_whenContiguous_returnsTrue() {
        List<LoadBalancingFlow> flows = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        boolean result = DifferenceFinder.prefixesAreContiguous(flows);

        assertThat(result, is(true));
    }

    @Test
    public void prefixesAreContiguous_whenNotContiguous_returnsFalse() {
        List<LoadBalancingFlow> flows = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("12.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        boolean result = DifferenceFinder.prefixesAreContiguous(flows);

        assertThat(result, is(false));
    }

    // Tests for same-range prefixes
    @Test
    public void prefixesCoverSameRange_whenBothEmpty_returnsTrue() {
        List<LoadBalancingFlow> flowsOld = emptyList();
        List<LoadBalancingFlow> flowsNew = emptyList();

        boolean result = DifferenceFinder.prefixesCoverSameRange(flowsOld, flowsNew);

        assertThat(result, is(true));
    }

    @Test
    public void prefixesCoverSameRange_whenOnlyOldEmpty_returnsFalse() {
        List<LoadBalancingFlow> flowsOld = emptyList();
        List<LoadBalancingFlow> flowsNew = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        boolean result = DifferenceFinder.prefixesCoverSameRange(flowsOld, flowsNew);

        assertThat(result, is(false));
    }

    @Test
    public void prefixesCoverSameRange_whenOnlyNewEmpty_returnsFalse() {
        List<LoadBalancingFlow> flowsOld = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        List<LoadBalancingFlow> flowsNew = emptyList();

        boolean result = DifferenceFinder.prefixesCoverSameRange(flowsOld, flowsNew);

        assertThat(result, is(false));
    }

    // Tests for transitions
    @Test
    public void transitions_whenBothEmpty_returnsEmptyList() {
        List<LoadBalancingFlow> flowsOld = emptyList();
        List<LoadBalancingFlow> flowsNew = emptyList();

        List<Transition> result = DifferenceFinder.transitions(flowsOld, flowsNew);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void transitions_whenSame_returnsPrefixes() {
        List<LoadBalancingFlow> flowsOld = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        List<LoadBalancingFlow> flowsNew = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.2")));

        List<Transition> result = DifferenceFinder.transitions(flowsOld, flowsNew);

        assertThat(result, equalTo(singletonList(
                new Transition(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))));
    }

    @Test
    public void transitions_whenOldSmaller_returnsSmaller() {
        List<LoadBalancingFlow> flowsOld = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/9"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.128.0.0/9"), IPv4Address.of("10.0.0.2")));
        List<LoadBalancingFlow> flowsNew = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.3")));

        List<Transition> result = DifferenceFinder.transitions(flowsOld, flowsNew);

        assertThat(result, equalTo(asList(
                new Transition(IPv4AddressWithMask.of("10.0.0.0/9"), IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.3")),
                new Transition(IPv4AddressWithMask.of("10.128.0.0/9"), IPv4Address.of("10.0.0.2"), IPv4Address.of("10.0.0.3")))));
    }

    @Test
    public void transitions_whenNewSmaller_returnsSmaller() {
        List<LoadBalancingFlow> flowsOld = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        List<LoadBalancingFlow> flowsNew = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/9"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.128.0.0/9"), IPv4Address.of("10.0.0.3")));

        List<Transition> result = DifferenceFinder.transitions(flowsOld, flowsNew);

        assertThat(result, equalTo(asList(
                new Transition(IPv4AddressWithMask.of("10.0.0.0/9"), IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")),
                new Transition(IPv4AddressWithMask.of("10.128.0.0/9"), IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.3")))));
    }

    // Tests for difference finder
    @Test
    public void findDifferences_whenSameEmptyFlows_returnsEmptyList() {
        List<LoadBalancingFlow> flowsOld = emptyList();
        List<LoadBalancingFlow> flowsNew = emptyList();

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void findDifferences_whenSameFlowsOneEach_returnsEmptyList() {
        List<LoadBalancingFlow> flowsOld = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        List<LoadBalancingFlow> flowsNew = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void findDifferences_whenSameFlowsTwoEach_returnsEmptyList() {
        List<LoadBalancingFlow> flowsOld = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.2")));
        List<LoadBalancingFlow> flowsNew = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.2")));

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void findDifferences_whenSameFlowsOutOfOrder_returnsEmptyList() {
        List<LoadBalancingFlow> flowsOld = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.2")));
        List<LoadBalancingFlow> flowsNew = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("11.0.0.0/8"), IPv4Address.of("10.0.0.2")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void findDifferences_whenOldFlowsNull_returnsEmptyList() {
        List<LoadBalancingFlow> flowsOld = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), null));
        List<LoadBalancingFlow> flowsNew = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void findDifferences_whenNewFlowsNull_returnsNewPrefixes() {
        List<LoadBalancingFlow> flowsOld = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        List<LoadBalancingFlow> flowsNew = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), null));

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(singletonList(
                new Transition(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1"), null))));
    }

    @Test
    public void findDifferences_whenNewFlowsDifferent_returnsNewPrefixes() {
        List<LoadBalancingFlow> flowsOld = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        List<LoadBalancingFlow> flowsNew = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.2")));

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(singletonList(
                new Transition(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))));
    }

    @Test
    public void findDifferences_whenNewFlowsSmaller_returnsSmallerAndDifferentPrefixes() {
        List<LoadBalancingFlow> flowsOld = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.1")));
        List<LoadBalancingFlow> flowsNew = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/9"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.128.0.0/9"), IPv4Address.of("10.0.0.2")));

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(singletonList(
                new Transition(IPv4AddressWithMask.of("10.128.0.0/9"), IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))));
    }

    @Test
    public void findDifferences_whenOldFlowsSmaller_returnsSmallerAndDifferentPrefixes() {
        List<LoadBalancingFlow> flowsOld = asList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/9"), IPv4Address.of("10.0.0.1")),
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.128.0.0/9"), IPv4Address.of("10.0.0.2")));
        List<LoadBalancingFlow> flowsNew = singletonList(
                new LoadBalancingFlow(IPv4AddressWithMask.of("10.0.0.0/8"), IPv4Address.of("10.0.0.2")));

        List<Transition> result = DifferenceFinder.findDifferences(flowsOld, flowsNew);

        assertThat(result, equalTo(singletonList(
                new Transition(IPv4AddressWithMask.of("10.0.0.0/9"), IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))));
    }

}
