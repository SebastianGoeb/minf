package com.sebastiangoeb.minf.driver;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
class RequestThread extends Thread {

    private Experiment experiment;
    private long startTimestamp;

    RequestThread(Experiment experiment) {
        super();
        this.experiment = experiment;
    }

    @Override
    public void run() {
        Traffic traffic = experiment.getTraffic();
        startTimestamp = System.currentTimeMillis();
        long endTimestamp = startTimestamp + traffic.getDuration() * Util.TO_MILLIS;

        while (System.currentTimeMillis() < endTimestamp && !Thread.interrupted()) {
            String localAddress = sampleLocalAddress();

            try {
                addLocalAddress(localAddress);
                requestData(localAddress);
            } finally {
                deleteLocalAddress(localAddress);
            }

            waitRandomInterArrivalTime();
        }
    }

    private void requestData(String localAddress) {
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
            long requestEndTime = (long) ((System.currentTimeMillis() - startTimestamp) * Util.TO_SECONDS);
            double requestDuration = (System.currentTimeMillis() - requestStartTimestamp) * Util.TO_SECONDS;
            String messageFormat = "%" + String.valueOf(traffic.getDuration()).length()
                    + "ds:\t%-16s %s (exit code: %-3d) duration: %.2f";
            System.out.println(String.format(messageFormat,
                    requestEndTime, localAddress, retval == 0 ? "SUCCESS" : "FAILURE", retval, requestDuration));
        } catch (IOException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int fakeRequestDuration(Traffic traffic) {
        try {
            double seconds = Util.parseUnits(traffic.getSize()) / Util.parseUnits(traffic.getRate());
            Thread.sleep((long) (seconds * Util.TO_MILLIS));
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    private void addLocalAddress(String localAddress) {
        String command = MessageFormat.format("ip addr add {0}/32 dev {1}",
                localAddress,
                experiment.getTraffic().getIntf());
        try {
            exec(command, experiment.isDryrun());
        } catch (IOException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void deleteLocalAddress(String localAddress) {
        String command = MessageFormat.format("ip addr del {0}/32 dev {1}",
                localAddress,
                experiment.getTraffic().getIntf());
        try {
            exec(command, experiment.isDryrun());
        } catch (IOException e) {
            System.out.println(MessageFormat.format("Unable to delete address {0}/32 from interface {1}",
                    localAddress, experiment.getTraffic().getIntf()));
            Thread.currentThread().interrupt();
        }
    }

    private String sampleLocalAddress() {
        String localSubnet = experiment.getTraffic().getLocalSubnet();
        Distribution localAddressDistribution = experiment.getTraffic().getLocalAddressDistribution();
        int minAddress = Util.ip2int(localSubnet.split("/")[0]);
        int maxAddress = minAddress + (1 << (32 - Integer.parseInt(localSubnet.split("/")[1]))) - 1;
        int sampledAddress = minAddress + (int) ((maxAddress - minAddress) * localAddressDistribution.sample());
        return Util.int2ip(sampledAddress);
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
                Thread.currentThread().interrupt();
            }
            return proc.exitValue();
        } else {
            return 0;
        }
    }

    private void waitRandomInterArrivalTime() {
        try {
            Util.stagger(experiment.getTraffic(), Math.random());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
