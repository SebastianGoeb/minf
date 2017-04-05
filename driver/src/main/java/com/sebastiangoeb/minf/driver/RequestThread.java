package com.sebastiangoeb.minf.driver;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

class RequestThread extends Thread {

    private static final List<String> FORBIDDEN_ADDRESSES;

    static {
        FORBIDDEN_ADDRESSES = asList(
                // VIP
                "10.5.1.12",
                // Dynamic VIPs
                "10.5.2.0",
                "10.5.2.1",
                "10.5.2.2",
                "10.5.2.3",
                // DIPs
                "10.0.0.1",
                "10.0.0.2",
                "10.0.0.3",
                "10.0.0.4",
                "10.0.0.5"
        );
    }

    private final Experiment experiment;
    private boolean cancelled;

    RequestThread(Experiment experiment) {
        super();
        this.experiment = experiment;
        this.cancelled = false;
    }

    @Override
    public void run() {
        Traffic traffic = experiment.getTraffic();
        long endTimestamp = experiment.getStartTimestamp() + traffic.getDuration() * Util.TO_MILLIS;

        while (System.currentTimeMillis() < endTimestamp && !cancelled) {
            String localAddress = null;
            try {
                localAddress = addLocalAddress();
                requestData(localAddress);
            } finally {
                deleteLocalAddress(localAddress);
            }

            waitRandomInterArrivalTime();
        }
    }

    private void requestData(String localAddress) {
        if (cancelled) {
            return;
        }
        Traffic traffic = experiment.getTraffic();
        String commandFormat = "wget -O /dev/null --bind-address {0} --limit-rate {1} http://{2}:8080/{3}";
        String command = MessageFormat.format(commandFormat,
                localAddress, traffic.getRate(), traffic.getRemoteAddress(), traffic.getSize());
        try {
            long requestStartTimestamp = System.currentTimeMillis();
            int retval = exec(command, experiment.isDryrun());
            if (experiment.isDryrun()) {
                retval = fakeRequestDuration(traffic);
            }
            long requestEndTime = (long) ((System.currentTimeMillis() - experiment.getStartTimestamp()) * Util.TO_SECONDS);
            double requestDuration = (System.currentTimeMillis() - requestStartTimestamp) * Util.TO_SECONDS;
            String messageFormat = "%" + String.valueOf(traffic.getDuration()).length()
                    + "ds:\t%-16s %s (exit code: %-3d) duration: %.2f";
            System.out.println(String.format(messageFormat,
                    requestEndTime, localAddress, retval == 0 ? "SUCCESS" : "FAILURE", retval, requestDuration));
        } catch (IOException ignored) {}

    }

    private int fakeRequestDuration(Traffic traffic) {
        try {
            double seconds = Util.parseUnits(traffic.getSize()) / Util.parseUnits(traffic.getRate());
            Thread.sleep((long) (seconds * Util.TO_MILLIS));
            return 0;
        } catch (InterruptedException e) {
            return 1;
        }
    }

    private String addLocalAddress() {
        synchronized (experiment) {
            String localSubnet = experiment.getTraffic().getLocalSubnet();
            CompositeDistribution localAddressDistribution = experiment.getTraffic().getLocalAddressDistribution();
            int minAddress = Util.ip2int(localSubnet.split("/")[0]);
            int maxAddress = minAddress + (1 << (32 - Integer.parseInt(localSubnet.split("/")[1]))) - 1;

            // Sample address
            int cycle = experiment.getTraffic().getCycle();
            int sampledAddressInt;
            String sampledAddress;
            do {
                if (cycle == 0) {
                    sampledAddressInt = minAddress + (int) ((maxAddress - minAddress) * localAddressDistribution.sample());

                } else {
                    sampledAddressInt = minAddress + (int) ((maxAddress - minAddress) * localAddressDistribution.sample(
                            (System.currentTimeMillis() - experiment.getStartTimestamp()) / 1000.0, cycle));
                }
                sampledAddress = Util.int2ip(sampledAddressInt);
            } while (FORBIDDEN_ADDRESSES.contains(sampledAddress));

            // increment address counter
            Map<String, Integer> activeLocalAddresses = experiment.getActiveLocalAddresses();
            activeLocalAddresses.compute(sampledAddress, (addr, i) -> i == null ? 1 : i + 1);
            if (activeLocalAddresses.get(sampledAddress) == 1) {
                String command = MessageFormat.format("ip addr add {0}/32 dev {1}",
                        sampledAddress,
                        experiment.getTraffic().getIntf());
                try {
                    exec(command, experiment.isDryrun());
                } catch (Exception e) {
                    cancelled = true;
                }
            }
            return sampledAddress;
        }
    }

    private void deleteLocalAddress(String localAddress) {
        if (localAddress == null) {
            return;
        }
        synchronized (experiment) {
            // Decrement address counter;
            Map<String, Integer> activeLocalAddresses = experiment.getActiveLocalAddresses();
            Integer i = activeLocalAddresses.get(localAddress);
            if (i == null || i <= 0) {
                throw new IllegalStateException(String.format("Address counter for %s may not be %d", localAddress, i));
            } else if (i > 1) {
                activeLocalAddresses.put(localAddress, i - 1);
            } else {
                activeLocalAddresses.remove(localAddress);
                String command = MessageFormat.format("ip addr del {0}/32 dev {1}",
                        localAddress,
                        experiment.getTraffic().getIntf());
                try {
                    exec(command, experiment.isDryrun());
                } catch (IOException e) {
                    System.out.println(MessageFormat.format("Unable to delete address {0}/32 from interface {1}",
                            localAddress, experiment.getTraffic().getIntf()));
                }
            }
        }
    }

    private int exec(String command, boolean dryRun) throws IOException {
        if (experiment.isVerbose()) {
            System.out.println(command);
        }
        if (!dryRun) {
            Process proc = new ProcessBuilder(command.split("\\s+"))
                    .redirectOutput(new File("/dev/null"))
                    .redirectErrorStream(true)
                    .start();
            try {
                proc.waitFor();
            } catch (InterruptedException ignored) {
                try {
                    proc.destroyForcibly().waitFor();
                } catch (InterruptedException ignored1) {}
            }
            return proc.exitValue();
        } else {
            return 0;
        }
    }

    private void waitRandomInterArrivalTime() {
        try {
            Util.stagger(experiment.getTraffic(), Math.random());
        } catch (InterruptedException ignored) {}
    }

    void cancel() {
        this.cancelled = true;
    }
}
