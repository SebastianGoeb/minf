package com.sebastiangoeb.minf.driver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class Experiment {

	// private static final String ADD_TUNNEL = "ip tun add tun0 mode gre remote {0} dev {1}";
	// private static final String DEL_TUNNEL = "ip tun del tun0";
	private static final String ADD_IP = "ip addr add {0}/32 dev {1}";
	private static final String DEL_IP = "ip addr del {0}/32 dev {1}";
	private static final String REQUEST = "wget -O /dev/null --bind-address {0} --limit-rate {1} http://{2}:8080/{3}";

	public String intf;
	public String remoteAddr;
	public List<Traffic> traffics;

	public void perform(boolean dryRun) {
		// Add tunnel
		// String command = MessageFormat.format(ADD_TUNNEL, remoteAddr, intf);
		// exec(command, dryRun);

		// Setup executor
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
		List<ScheduledFuture<?>> futures = new ArrayList<>();

		// Run traffic generation
		for (Traffic traffic : traffics) {
			long time = System.currentTimeMillis();

			// Add new threads
			executor.setCorePoolSize(traffic.clients);
			while (futures.size() < traffic.clients) {
				futures.add(executor.scheduleWithFixedDelay(() -> {
					double val = Util.sampleDistributions(traffic.localAddr);
					String localAddress = Util.formatIP(val);

					// Add IP
					exec(MessageFormat.format(ADD_IP, localAddress, intf), dryRun);

					// Request data
					exec(MessageFormat.format(REQUEST, localAddress, traffic.rate, remoteAddr, traffic.size), dryRun);
					if (dryRun) {
						try {
							Thread.sleep((long) ((double) Util.parseUnits(traffic.size) / Util.parseUnits(traffic.rate)
									* 1000));
						} catch (InterruptedException e) {
						}
					}

					// Delete IP
					exec(MessageFormat.format(DEL_IP, localAddress, intf), dryRun);
				}, 0, 1, TimeUnit.NANOSECONDS));
			}

			// Sleep main thread until next traffic pattern
			long duration = Math.round(Util.parseUnits(traffic.duration) * 1000);
			try {
				Thread.sleep(Math.max(0, duration - (System.currentTimeMillis() - time)));
			} catch (InterruptedException e) {
				System.out.println("Interrupted while sleeping");
				e.printStackTrace();
			} finally {
				// Mark previous threads for cancellation
				futures.forEach(f -> f.cancel(false));
				futures.clear();
			}
		}

		// Teardown executor
		try {
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			System.out.println("Interruption waiting for executor shutdown");
			e.printStackTrace();
		}

		// Delete tunnel
		// command = MessageFormat.format(DEL_TUNNEL, remoteAddr, intf);
		// exec(command, dryRun);
	}

	public void exec(String command, boolean dryRun) {
		try {
			System.out.println(command);
			if (!dryRun) {
				new ProcessBuilder(command.split("\\s+")).redirectOutput(new File("/dev/null"))
						.redirectErrorStream(true).start().waitFor();
			}
		} catch (IOException | InterruptedException e) {
			System.out.println(MessageFormat.format("Error executing: {0}", command));
			e.printStackTrace();
		}
	}

	public static Experiment fromFile(String fileName) {
		try {
			return new Gson().fromJson(new FileReader(fileName), Experiment.class);
		} catch (JsonSyntaxException e) {
			System.out.println("Invalid json file: " + fileName);
		} catch (JsonIOException e) {
			System.out.println("Error reading file: " + fileName);
		} catch (FileNotFoundException e) {
			System.out.println("No such file: " + fileName);
		}
		return null;
	}
}
