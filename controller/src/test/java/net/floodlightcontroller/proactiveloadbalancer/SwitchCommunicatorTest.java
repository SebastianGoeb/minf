package net.floodlightcontroller.proactiveloadbalancer;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.test.FloodlightTestCase;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.easymock.EasyMock.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@SuppressWarnings("ConstantConditions")
@RunWith(EasyMockRunner.class)
public class SwitchCommunicatorTest extends FloodlightTestCase {

    @Mock
    private IOFSwitch mockSwitch;

    @Mock
    private IOFSwitch mockSwitch2;

    @Mock
    private Function<IOFSwitch, String> mockFunction;

    @Test
    public void concurrently_whenSwitchesNull_returnsDefault() {
        List<IOFSwitch> switches = null;
        Function<IOFSwitch, ?> function = iofSwitch -> null;

        Map<DatapathId, ?> result = SwitchCommunicator.concurrently(switches, function);

        assertThat(result, is(SwitchCommunicator.defaultResult()));
    }

    @Test
    public void concurrently_whenSwitchesEmpty_returnsDefault() {
        List<IOFSwitch> switches = emptyList();
        Function<IOFSwitch, ?> function = iofSwitch -> null;

        Map<DatapathId, ?> result = SwitchCommunicator.concurrently(switches, function);

        assertThat(result, is(SwitchCommunicator.defaultResult()));
    }

    @Test
    public void concurrently_whenFunctionNull_returnsDefault() {
        List<IOFSwitch> switches = emptyList();
        Function<IOFSwitch, ?> function = null;

        Map<DatapathId, ?> result = SwitchCommunicator.concurrently(switches, function);

        assertThat(result, is(SwitchCommunicator.defaultResult()));
    }

    @Test
    public void concurrently_whenCalled_callsFunction() {
        List<IOFSwitch> switches = singletonList(mockSwitch);
        Function<IOFSwitch, String> function = mockFunction;

        expect(function.apply(mockSwitch)).andReturn(null);
        replay(function);

        SwitchCommunicator.concurrently(switches, function);

        verify(function);
    }

    @Test
    public void concurrently_whenSingleSwitch_returnsSingleResult() {
        List<IOFSwitch> switches = singletonList(mockSwitch);
        Function<IOFSwitch, String> function = mockFunction;

        expect(mockSwitch.getId()).andStubReturn(DatapathId.of(1));
        expect(function.apply(mockSwitch)).andStubReturn("A");
        replay(mockSwitch);
        replay(function);

        Map<DatapathId, String> result = SwitchCommunicator.concurrently(switches, function);

        assertThat(result, equalTo(singletonMap(DatapathId.of(1), "A")));
    }

    @Test
    public void concurrently_whenMultipleSwitch_returnsMultipleResults() {
        List<IOFSwitch> switches = asList(mockSwitch, mockSwitch2);
        Function<IOFSwitch, String> function = mockFunction;

        expect(mockSwitch.getId()).andStubReturn(DatapathId.of(1));
        expect(mockSwitch2.getId()).andStubReturn(DatapathId.of(2));
        expect(function.apply(mockSwitch)).andStubReturn("A");
        expect(function.apply(mockSwitch2)).andStubReturn("B");
        replay(mockSwitch);
        replay(mockSwitch2);
        replay(function);

        Map<DatapathId, String> result = SwitchCommunicator.concurrently(switches, function);

        Map<DatapathId, String> expectedResult = new HashMap<>();
        expectedResult.put(DatapathId.of(1), "A");
        expectedResult.put(DatapathId.of(2), "B");
        assertThat(result, equalTo(expectedResult));
    }
}
