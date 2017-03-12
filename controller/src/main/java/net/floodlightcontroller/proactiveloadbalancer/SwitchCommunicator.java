package net.floodlightcontroller.proactiveloadbalancer;

import com.google.common.collect.Iterables;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import static java.util.Collections.emptyMap;

class SwitchCommunicator {

    static <T> Map<DatapathId, T> defaultResult() {
        return emptyMap();
    }

    static <T> Map<DatapathId, T> concurrently(Iterable<IOFSwitch> switches, Function<IOFSwitch, T> function) {
        if (switches == null || Iterables.isEmpty(switches) || function == null) {
            return defaultResult();
        }

        // Setup threads (dedup dpids)
        Map<DatapathId, CommunicatorThread<T>> threads = new HashMap<>();
        for (IOFSwitch iofSwitch : switches) {
            DatapathId dpid = iofSwitch.getId();
            if (!threads.containsKey(dpid)) {
                threads.put(dpid, new CommunicatorThread<>(iofSwitch, function));
            }
        }

        // Run and wait
        threads.values().forEach(Thread::start);
        threads.values().forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException ignored) {}
        });

        // Collect results
        Map<DatapathId, T> results = new HashMap<>();
        for (Entry<DatapathId, CommunicatorThread<T>> entry : threads.entrySet()) {
            results.put(entry.getKey(), entry.getValue().getResult());
        }
        return results;
    }

    public static class CommunicatorThread<T> extends Thread {
        private T result;
        private IOFSwitch iofSwitch;
        private Function<IOFSwitch, T> function;

        private CommunicatorThread(IOFSwitch iofSwitch, Function<IOFSwitch, T> function) {
            super();
            this.iofSwitch = iofSwitch;
            this.function = function;
        }

        @Override
        public void run() {
            result = function.apply(iofSwitch);
        }

        public T getResult() {
            return result;
        }
    }
}
