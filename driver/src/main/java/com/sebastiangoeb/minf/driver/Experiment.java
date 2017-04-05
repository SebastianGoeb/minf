package com.sebastiangoeb.minf.driver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

class Experiment {

	private Traffic traffic;
	private boolean dryrun;
	private boolean verbose;
	private long startTimestamp;
	private Map<String, Integer> activeLocalAddresses;

	private Experiment(Traffic traffic, boolean dryrun, boolean verbose) {
		this.traffic = traffic;
		this.dryrun = dryrun;
		this.verbose = verbose;
	}

	static Experiment fromStream(InputStream inputStream, boolean dryRun, boolean verbose) {
		try {
			Gson gson = new GsonBuilder()
					.registerTypeAdapter(CompositeDistribution.class, new CompositeDistributionDeserializer())
					.create();
			Traffic traffic = gson.fromJson(new InputStreamReader(inputStream), Traffic.class);
			System.out.println(traffic.toString());
			return new Experiment(traffic, dryRun, verbose);
		} catch (JsonSyntaxException | JsonIOException e) {
			e.printStackTrace();
			System.exit(1);
			throw new RuntimeException();
		}
	}

	static Experiment fromFile(String fileName, boolean dryRun, boolean verbose) {
		try {
			return fromStream(new FileInputStream(fileName), dryRun, verbose);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
			throw new RuntimeException();
		}
	}

	void perform() {
		startTimestamp = System.currentTimeMillis();
		activeLocalAddresses = new HashMap<>();
		List<RequestThread> threads = createThreads();
		registerShutdownHook(threads);
		startThreads(threads);
	}

	private List<RequestThread> createThreads() {
		List<RequestThread> threads = new ArrayList<>();
		for (int i = 0; i < traffic.getClients(); i++) {
			threads.add(new RequestThread(this));
		}
		return threads;
	}

	private void registerShutdownHook(List<RequestThread> threads) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Waiting for client threads to shut down");
			interruptThreads(threads);
			waitForThreads(threads);
			System.out.println("Client threads shut down successfully");
		}));
	}

	private void startThreads(List<RequestThread> threads) {
		try {
			for (Thread thread : threads) {
				thread.start();
				Util.stagger(traffic, 1);
			}
		} catch (InterruptedException e) {
			if (verbose) {
				System.out.println("Interrupted while creating threads");
			}
		}
	}

	private void interruptThreads(List<RequestThread> threads) {
		for (RequestThread thread : threads) {
			thread.cancel();
			thread.interrupt();
		}
	}

	private void waitForThreads(List<? extends Thread> threads) {
		try {
			for (Thread thread : threads) {
				thread.join();
			}
		} catch (InterruptedException e) {
			if (verbose) {
				System.out.println("Interrupted while waiting for client threads to shut down");
			}
		}
	}

	Traffic getTraffic() {
		return traffic;
	}

	boolean isDryrun() {
		return dryrun;
	}

	boolean isVerbose() {
		return verbose;
	}

	long getStartTimestamp() {
		return startTimestamp;
	}

	Map<String, Integer> getActiveLocalAddresses() {
		return activeLocalAddresses;
	}
}
