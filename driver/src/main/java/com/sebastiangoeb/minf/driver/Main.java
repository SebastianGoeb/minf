package com.sebastiangoeb.minf.driver;

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
		options.addOption(Option.builder("e").longOpt("experiment").argName("exp").desc("Experiment json file").hasArg().required().build());
		options.addOption(Option.builder("d").longOpt("dry-run").argName("dry run").desc("Don't run any commands. Just print them.").build());

		// Parse
		try {
			CommandLine cli = new DefaultParser().parse(options, args);
			return new Config(cli.getOptionValue("e"), cli.hasOption("d"));
		} catch (ParseException exp) {
			System.out.println(exp.getMessage());
			System.exit(1);
			return null;
		}
	}

	public static void main(String[] args) {
		Config config = parseArgs(args);
		Experiment experiment = Experiment.fromFile(config.getExperimentPath());
		experiment.perform(config.isDryRun());
	}
}
