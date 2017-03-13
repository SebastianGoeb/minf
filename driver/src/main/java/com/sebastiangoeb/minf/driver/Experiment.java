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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class Experiment {

	private static final String ADD_IP = "ip addr add {0}/32 dev {1}";
	private static final String DEL_IP = "ip addr del {0}/32 dev {1}";
	private static final String GET_DATA = "wget -O /dev/null --bind-address {0} --limit-rate {1} --tries={2} --timeout={3} http://{4}:8080/{5}";

	public String intf;
	public String remoteAddr;
	public String localSubnet;
	public List<Traffic> traffics;

	public static Experiment fromFile(String fileName) {
		try {
			return new GsonBuilder()
					.registerTypeAdapter(Distribution.class, new DistributionDeserializer())
					.create()
					.fromJson(new FileReader(fileName), Experiment.class);
		} catch (JsonSyntaxException e) {
			System.out.println("JSON syntax exception: " + fileName);
			e.printStackTrace();
		} catch (JsonIOException e) {
			System.out.println("Error reading file: " + fileName);
		} catch (FileNotFoundException e) {
			System.out.println("No such file: " + fileName);
		}
		System.exit(1);
		return null;
	}

	public void perform(boolean dryRun) {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
		List<ScheduledFuture<?>> futures = new ArrayList<>();

		int minIp = Util.ip2int(localSubnet.split("/")[0]);
		int maxIp = minIp + (1 << (32 - Integer.parseInt(localSubnet.split("/")[1]))) - 1;

		// Run traffic generation
		for (Traffic traffic : traffics) {
			long time = System.currentTimeMillis();

			// Add new threads
			executor.setCorePoolSize(traffic.clients);
			while (futures.size() < traffic.clients) {
				futures.add(executor.scheduleWithFixedDelay(() -> {
					int val = minIp + (int) ((maxIp - minIp) * traffic.localAddr.sample());
					String localAddress = Util.int2ip(val);

					// Add IP
					exec(MessageFormat.format(ADD_IP, localAddress, intf), dryRun);

					// Request data
					String command = MessageFormat.format(GET_DATA, localAddress, traffic.rate, remoteAddr, traffic.size);
					System.out.println(command);
					int retval = exec(command, dryRun);
					if (dryRun) {
						try {
							Thread.sleep((long) ((double) Util.parseUnits(traffic.size) / Util.parseUnits(traffic.rate)
									* 1000));
						} catch (InterruptedException e) {
						}
					}
					if (retval == 0) {
						System.out.println(localAddress + "\tsuccess: " + retval);
					} else {
						System.out.println(localAddress + "\tfailure: " + retval);
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
	}

	public int exec(String command, boolean dryRun) {
		try {
			if (!dryRun) {
				Process proc = new ProcessBuilder(command.split("\\s+")).redirectOutput(new File("/dev/null"))
						.redirectErrorStream(true).start();
				proc.waitFor();
				return proc.exitValue();
			} else {
				return 0;
			}
		} catch (IOException | InterruptedException e) {
			System.out.println(MessageFormat.format("Error executing: {0}", command));
			e.printStackTrace();
			return -1;
		}
	}
}
