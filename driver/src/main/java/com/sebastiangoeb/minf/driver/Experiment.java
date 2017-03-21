package com.sebastiangoeb.minf.driver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class Experiment {

	private static final String ADD_IP = "ip addr add {0}/32 dev {1}";
	private static final String DEL_IP = "ip addr del {0}/32 dev {1}";
	private static final String GET_DATA = "wget -O /dev/null --bind-address {0} --limit-rate {1} --tries={2} --timeout={3} http://{4}:8080/{5}";

	@SuppressWarnings("unused")
	private String intf;

	@SuppressWarnings("unused")
	private String remoteAddr;

	@SuppressWarnings("unused")
	private String localSubnet;

	@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
	private List<Traffic> traffics;

	static Experiment fromStream(InputStream inputStream) {
		try {
			return new GsonBuilder().registerTypeAdapter(Distribution.class, new DistributionDeserializer()).create()
					.fromJson(new InputStreamReader(inputStream), Experiment.class);
		} catch (JsonSyntaxException | JsonIOException e) {
			e.printStackTrace();
			System.exit(1);
			throw new RuntimeException();
		}
	}

	static Experiment fromFile(String fileName) {
		try {
			return fromStream(new FileInputStream(fileName));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
			throw new RuntimeException();
		}
	}

	void perform(boolean dryRun, boolean verbose) {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
		List<ScheduledFuture<?>> futures = new ArrayList<>();

		int minIp = Util.ip2int(localSubnet.split("/")[0]);
		int maxIp = minIp + (1 << (32 - Integer.parseInt(localSubnet.split("/")[1]))) - 1;

		// Run traffic generation
		for (Traffic traffic : traffics) {
			long time = System.currentTimeMillis();
			double theoreticalRequestsPerSecond = Util.parseUnits(traffic.rate) / Util.parseUnits(traffic.size) * traffic.clients;

			// Add new threads
			executor.setCorePoolSize(traffic.clients);
			while (futures.size() < traffic.clients) {
				futures.add(executor.scheduleWithFixedDelay(() -> {
					int val = minIp + (int) ((maxIp - minIp) * traffic.localAddr.sample());
					String localAddress = Util.int2ip(val);
					
					try {
						// Add IP
						String addIpCommand = MessageFormat.format(ADD_IP, localAddress, intf);
						if (verbose) {
							System.out.println(addIpCommand);
						}
						exec(addIpCommand, dryRun);
	
						// Request data
						String requestCommand = MessageFormat.format(GET_DATA, localAddress, traffic.rate, 0, 0, remoteAddr, traffic.size);
						if (verbose) {
							System.out.println(requestCommand);
						}
						long startTime = System.currentTimeMillis();
						int retval = exec(requestCommand, dryRun);
						if (dryRun) {
							try {
								Thread.sleep((long) (Util.parseUnits(traffic.size) / Util.parseUnits(traffic.rate)
										* 1000));
							} catch (InterruptedException ignored) {
							}
						}
						double duration = (System.currentTimeMillis() - startTime) / 1000.0;
						if (retval == 0) {
							System.out.println(localAddress + "\tSUCCESS retval: " + retval + "\tduration: " + duration);
						} else {
							System.out.println(localAddress + "\tFAILURE retval: " + retval + "\tduration: " + duration);
						}
					} finally {
						// Delete IP
						String delIpCommand = MessageFormat.format(DEL_IP, localAddress, intf);
						if (verbose) {
							System.out.println(delIpCommand);
						}
						exec(delIpCommand, dryRun);
					}

					// Randomize flow inter-arrival time
					try {
						Thread.sleep((long) (Math.random() / theoreticalRequestsPerSecond * 1000));
					} catch (InterruptedException ignored) {
					}
				}, 0, 1, TimeUnit.NANOSECONDS));
				
				// Stagger requests
				try {
					Thread.sleep((long) (2 / theoreticalRequestsPerSecond * 1000));
				} catch (InterruptedException ignored) {
				}
			}

			// Sleep main thread until next traffic pattern
			long duration = Math.round(Util.parseUnits(traffic.duration) * 1000);
			try {
				Thread.sleep(Math.max(0, duration - (System.currentTimeMillis() - time)));
			} catch (InterruptedException e) {
				if (verbose) {
					System.out.println("Interrupted while waiting for traffic pattern to complete");
				}
				e.printStackTrace();
			} finally {
				// Mark previous threads for cancellation
				if (verbose) {
					System.out.println("Cancelling futures");
				}
				futures.forEach(f -> f.cancel(false));
				futures.clear();
			}
		}

		// Shutdown executor
		try {
			if (verbose) {
				System.out.println("Shutting down executor");
			}
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			if (verbose) {
				System.out.println("Interrupted while waiting for executor to shutdown");
			}
			e.printStackTrace();
		}
		if (verbose) {
			System.out.println("Executor shutdown complete");
		}
	}

	private int exec(String command, boolean dryRun) {
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
