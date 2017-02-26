package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.proactiveloadbalancer.domain.WeightedPrefix;
import net.floodlightcontroller.proactiveloadbalancer.util.IPv4AddressRange;
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

public class MeasurementMergerTest extends FloodlightTestCase {

    @Test
    public void merge_whenEmptyInput_returnsDefault() {
        List<WeightedPrefix> measurements = emptyList();

        List<WeightedPrefix> result = MeasurementMerger.merge(measurements, null);

        assertThat(result, equalTo(emptyList()));
    }

    @Test
    public void merge_whenSingleInput_returnsInput() {
        List<WeightedPrefix> measurements = singletonList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1));

        List<WeightedPrefix> result = MeasurementMerger.merge(measurements, null);

        List<WeightedPrefix> expectedResult = singletonList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void merge_whenNonOverlappingInputs_returnsInputs() {
        List<WeightedPrefix> measurements = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("11.0.0.0/8"), 1));
        IPv4AddressRange clientRange = IPv4AddressRange.of(IPv4Address.of("10.0.0.0"), IPv4Address.of("11.255.255.255"));

        List<WeightedPrefix> result = MeasurementMerger.merge(measurements, clientRange);

        List<WeightedPrefix> expectedResult = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("11.0.0.0/8"), 1));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void merge_whenOutOfOrderInputs_returnsInputsOrdered() {
        List<WeightedPrefix> measurements = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("11.0.0.0/8"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1));
        IPv4AddressRange clientRange = IPv4AddressRange.of(IPv4Address.of("10.0.0.0"), IPv4Address.of("11.255.255.255"));

        List<WeightedPrefix> result = MeasurementMerger.merge(measurements, clientRange);

        List<WeightedPrefix> expectedResult = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("11.0.0.0/8"), 1));
        assertThat(result, equalTo(expectedResult));
    }

    @Test
    public void merge_whenOverlappingInputs_splitsWeightOfLargerPrefixes() {
        List<WeightedPrefix> measurements = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/8"), 1),
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/9"), 1));
        IPv4AddressRange clientRange = IPv4AddressRange.of(IPv4AddressWithMask.of("10.0.0.0/8"));

        List<WeightedPrefix> result = MeasurementMerger.merge(measurements, clientRange);

        List<WeightedPrefix> expectedResult = asList(
                new WeightedPrefix(IPv4AddressWithMask.of("10.0.0.0/9"), 1.5),
                new WeightedPrefix(IPv4AddressWithMask.of("10.128.0.0/9"), 0.5));
        assertThat(result, equalTo(expectedResult));
    }
}