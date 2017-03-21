package com.sebastiangoeb.minf.driver;

import org.apache.commons.cli.*;

import java.util.List;

public class Main {
	
	private static class Config {
		private String experimentPath;
		private boolean dryRun;
		private boolean verbose;
		
		private Config(String experimentPath, boolean dryRun, boolean verbose) {
			super();
			this.experimentPath = experimentPath;
			this.dryRun = dryRun;
			this.verbose = verbose;
		}

		private String getExperimentPath() {
			return experimentPath;
		}

		private boolean isDryRun() {
			return dryRun;
		}

		private boolean isVerbose() {
			return verbose;
		}
	}

	private static Config parseArgs(String[] args) {
		// Create options
		Options options = new Options();
		options.addOption(Option.builder("d").longOpt("dry-run").argName("dry run").desc("Don't run any commands. Just print them.").build());
		options.addOption(Option.builder("v").longOpt("verbose").argName("verbose output").desc("More detailed output").build());

		// Parse
		try {
			CommandLine cli = new DefaultParser().parse(options, args);
			boolean dryRun = cli.hasOption("d");
			boolean verbose = cli.hasOption("v");
			List<String> argList = cli.getArgList();
			if (argList.size() == 0) {
				return new Config(null, dryRun, verbose);
			} else if (argList.size() == 1) {
				String experimentPath = argList.stream().findFirst().orElse(null);
				return new Config(experimentPath, dryRun, verbose);
			} else {
				System.out.println("Please provide only one experiment json file or use stdin");
				System.exit(1);
				throw new RuntimeException();
			}
		} catch (ParseException exp) {
			System.out.println(exp.getMessage());
			System.exit(1);
			throw new RuntimeException();
		}
	}

	public static void main(String[] args) {
		Config config = parseArgs(args);
		if (config.getExperimentPath() == null) {
			Experiment.fromStream(System.in).perform(config.isDryRun(), config.isVerbose());
		} else {
			Experiment.fromFile(config.getExperimentPath()).perform(config.isDryRun(), config.isVerbose());
		}
	}
}
