package com.sebastiangoeb.minf.driver;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {
	
	private static class Config {
		private String experimentPath;
		private boolean dryRun;
		
		private Config(String experimentPath, boolean dryRun) {
			super();
			this.experimentPath = experimentPath;
			this.dryRun = dryRun;
		}

		private String getExperimentPath() {
			return experimentPath;
		}

		private boolean isDryRun() {
			return dryRun;
		}
	}

	public static Config parseArgs(String[] args) {
		// Create options
		Options options = new Options();
		options.addOption(Option.builder("d").longOpt("dry-run").argName("dry run").desc("Don't run any commands. Just print them.").build());

		// Parse
		try {
			CommandLine cli = new DefaultParser().parse(options, args);
			boolean dryRun = cli.hasOption("d");
			List<String> argList = cli.getArgList();
			if (argList.size() == 0) {
				return new Config(null, dryRun);
			} else if (argList.size() == 1) {
				String experimentPath = argList.stream().findFirst().orElse(null);
				return new Config(experimentPath, dryRun);
			} else {
				System.out.println("Please provide only one experiment json file or use stdin");
				System.exit(1);
				return null;
			}
		} catch (ParseException exp) {
			System.out.println(exp.getMessage());
			System.exit(1);
			return null;
		}
	}

	public static void main(String[] args) {
		Config config = parseArgs(args);
		if (config.getExperimentPath() == null) {
			Experiment.fromStream(System.in).perform(config.isDryRun());
		} else {
			Experiment.fromFile(config.getExperimentPath()).perform(config.isDryRun());
		}
	}
}
