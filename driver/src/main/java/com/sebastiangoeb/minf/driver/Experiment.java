package com.sebastiangoeb.minf.driver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

class Experiment {

	private Traffic traffic;
	private boolean dryrun;
	private boolean verbose;

	private Experiment(Traffic traffic, boolean dryrun, boolean verbose) {
		this.traffic = traffic;
		this.dryrun = dryrun;
		this.verbose = verbose;
	}

	static Experiment fromStream(InputStream inputStream, boolean dryRun, boolean verbose) {
		try {
			Gson gson = new GsonBuilder()
					.registerTypeAdapter(Distribution.class, new DistributionDeserializer())
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
			if (verbose) {
				System.out.println("Waiting for client threads to shut down");
			}
			interruptThreads(threads);
			waitForThreads(threads);
			if (verbose) {
				System.out.println("Client threads shut down successfully");
			}
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

	private void interruptThreads(List<? extends Thread> threads) {
		for (Thread thread : threads) {
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
}
