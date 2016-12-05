package com.sebastiangoeb.minf.driver;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {

	public static CommandLine parseArgs(String[] args) {
		// create parser
		CommandLineParser parser = new DefaultParser();

		// create options
		Options options = new Options();
//		options.addOption(Option.builder("t").longOpt("topology").argName("topo").desc("Topology json file").hasArg()
//				.required().build());
		options.addOption(Option.builder("e").longOpt("experiment").argName("exp").desc("Experiment json file").hasArg()
				.required().build());
		options.addOption(Option.builder("d").longOpt("dry-run").argName("dry run").desc("Don't run any commands. Just print them.").build());

		// parse
		try {
			return parser.parse(options, args);
		} catch (ParseException exp) {
			System.out.println(exp.getMessage());
			return null;
		}
	}

	public static void main(String[] args) {
		// parse args
		CommandLine cli = parseArgs(args);
		if (cli == null) {
			System.exit(1);
		}

		// load files
//		Topology topo = Topology.fromFile(cli.getOptionValue("t"));
		Experiment exp = Experiment.fromFile(cli.getOptionValue("e"));
		if (exp == null) {
			System.exit(1);
		}

		// run experiment
		exp.perform(cli.hasOption("d"));
	}
}
