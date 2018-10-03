/*******************************************************************************
 * MKTR - Minimal k-Treewidth Relaxation
 *
 * Copyright (C) 2018 
 * Max Waters (max.waters@rmit.edu.au)
 * RMIT University, Melbourne VIC 3000
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package au.rmit.agtgrp.pp.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;

import au.rmit.agtgrp.csp.ExpressionCsp;
import au.rmit.agtgrp.csp.solver.CspSolver;
import au.rmit.agtgrp.csp.solver.GeCodeInterface;
import au.rmit.agtgrp.csp.solver.ZincFormatter;
import au.rmit.agtgrp.lang.pddl.Plan;
import au.rmit.agtgrp.lang.pddl.PlanFactory;
import au.rmit.agtgrp.lang.pddl.PddlProblem.PlanResult;
import au.rmit.agtgrp.lang.pddl.parser.PddlFormatter;
import au.rmit.agtgrp.lang.pddl.parser.PddlParser;
import au.rmit.agtgrp.lang.pddl.parser.PddlParserException;
import au.rmit.agtgrp.pp.mktr.MinKTreewidthRelaxation;
import au.rmit.agtgrp.pp.mktr.MktrResult;
import au.rmit.agtgrp.pp.partialplan.PartialPlan;
import au.rmit.agtgrp.pp.partialplan.pcplan.encoder.PcToCspEncoderException;
import au.rmit.agtgrp.pp.partialplan.pcplan.optimiser.CspOptimiserException;
import au.rmit.agtgrp.pp.partialplan.planset.PlanSet;
import au.rmit.agtgrp.pp.mktr.policy.RelaxationPolicyException;
import au.rmit.agtgrp.utils.collections.graph.treewidth.TreewidthCalculator;

public class MktrMain {

	public static final File OPTS_FILE = new File("mktr.props");
	public static final File TEMP_DIR = new File("mktr_temp");

	private static MktrOptions options;
	
	public static void main(String[] args) {

		// parse command line
		options = parseCommandLine(args);

		// clean from previous
		recursivelyDelete(TEMP_DIR);
		TEMP_DIR.mkdirs();
		options.outCspFile.getAbsoluteFile().delete();
		options.outPlansFile.getAbsoluteFile().delete();

		// load pddl
		PddlParser pddlParser = loadPDDL(options);

		// initialise external tools
		CspSolver cspSolver = new GeCodeInterface(TEMP_DIR);
		TreewidthCalculator twCalc = new TreewidthCalculator();

		try {		
			// get plan
			Plan plan = pddlParser.getPlan();

			// add required formatting -- unique var names, etc
			plan = PlanFactory.formatAsPlan(plan.getProblem(), plan.getGroundSteps(), true, true);
			
			// run mktr
			MinKTreewidthRelaxation mktr = new MinKTreewidthRelaxation(
					plan, options.encoderName,
					options.policyName, options.treewidth, options.nPerStep,
					options.mktrTime, options.validate, 
					options.verbose, twCalc, cspSolver);

			mktr.relax();

			// write/serialize CSP
			PartialPlan pp = mktr.getFinalPartialPlan();
			System.out.println("Writing final CSP to " + options.outCspFile.getAbsolutePath());		
			writeCsp(pp.getConstraints(), options.outCspFile);
			if (options.outCspDatFile != null) {
				System.out.println("Serializing final CSP to " + options.outCspDatFile.getAbsolutePath());		
				serializeCsp(pp.getConstraints(), options.outCspDatFile);
			}
			
			if (options.maxPrint == 0) {
				// print plan count
				MktrResult result = mktr.getInstantiationCount(options.planCountTime);
				System.out.println("MKTR found " + result.count + " instantiation" + 
						(result.count == 1 ? "" : "s") + 
						(result.timedout ? " (re-instantiation count timed out)" : ""));			
			}
			else {
				// write final plans
				MktrResult result = mktr.getReinstantiations(options.planCountTime);
				System.out.println("MKTR found " + result.count + " instantiation" + 
						(result.count == 1 ? "" : "s") + 
						(result.timedout ? " (re-instantiation generation timed out)" : ""));
				
				if (result.count > 0) {
					int max = options.maxPrint < 0 ? Integer.MAX_VALUE : options.maxPrint;
					max = Math.min(max, result.count);
					System.out.println("Writing " + max + " instantiation" + (max == 1 ? "" : "s") +
							" to " + options.outPlansFile.getAbsolutePath());
		
					writePlans(result.plans, options.outPlansFile, max);
				}
			}

		}			
		catch (RelaxationPolicyException | PcToCspEncoderException  e) {
			System.err.println(e.getMessage());
			if (options.verbose)
				e.printStackTrace();
			System.exit(1);
		}
		catch (CspOptimiserException e) {
			System.err.println("Unexpected error while optimising CSP");
			e.printStackTrace();
			System.exit(1);
		}
		catch (Exception e) {
			System.err.println("Unexpected error");
			e.printStackTrace();
			System.exit(1);
		}

		// clean up
		recursivelyDelete(TEMP_DIR);

	}

	private static MktrOptions parseCommandLine(String[] args) {
		ParserProperties properties = ParserProperties.defaults();
		properties.withOptionSorter(null);

		MktrOptions options = new MktrOptions();
		CmdLineParser optionParser = new CmdLineParser(options, properties);

		StringWriter usage = new StringWriter();
		optionParser.printUsage(usage, null);

		List<String> optionStrs = getFileOptions(options);
		for (String arg : args)
			optionStrs.add(arg);

		try {
			optionParser.parseArgument(optionStrs);

		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println(usage);
			System.exit(1);
		}

		// print help message if requested
		if (options.help) {
			System.out.println(usage);
			System.exit(0);
		}

		return options;
	}

	private static void writePlans(PlanSet partialPlan, File output, int max) {

		try (BufferedWriter writer = Files.newBufferedWriter(output.toPath())) {
			output = output.getAbsoluteFile();
			output.getParentFile().mkdirs();
			int n = 0;
			for (Plan plan : partialPlan) {
				writer.write(PddlFormatter.getPlanString(plan));
				writer.write("\n;;;\n");

				if (++n == max)
					break;
			}
		} catch (IOException e) {
			System.err.println("Error writing final plans to " + output + ": " + e.getMessage());
			if (options.verbose)
				e.printStackTrace();
			System.exit(1);
		}
	}

	private static void writeCsp(ExpressionCsp csp, File output) {

		try (BufferedWriter writer = Files.newBufferedWriter(output.toPath())) {
			output = output.getAbsoluteFile();
			output.getParentFile().mkdirs();
			ZincFormatter zf = new ZincFormatter(csp);
			writer.write(zf.getZincString());
		} catch (IOException e) {
			System.err.println("Error writing CSP to " + output + ": " + e.getMessage());
			if (options.verbose)
				e.printStackTrace();
			System.exit(1);
		}
	}
	
	private static void serializeCsp(ExpressionCsp csp, File output) {

		try (BufferedWriter writer = Files.newBufferedWriter(output.toPath())) {
			output = output.getAbsoluteFile();
			output.getParentFile().mkdirs();
			
			ExpressionCsp.serialize(csp, output);
			
		} catch (IOException e) {
			System.err.println("Error serializing CSP to " + output + ": " + e.getMessage());
			if (options.verbose)
				e.printStackTrace();
			System.exit(1);
		}
	}

	private static PddlParser loadPDDL(MktrOptions parsedArgs) {

		PddlParser pddlParser = new PddlParser();

		try {
			pddlParser.parse(parsedArgs.domainFile, parsedArgs.problemFile);
			pddlParser.parseFDPlan(parsedArgs.planFile);

		}  catch (PddlParserException e) {
			System.err.println(e.getMessage());
			if (options.verbose)
				e.printStackTrace();
			System.exit(1);
		} catch (Exception e) {
			System.err.println("Unexpected error while parsing PDDL");
			e.printStackTrace();
			System.exit(1);
		}

		// check that the plan is valid
		PlanResult pr = pddlParser.getProblem().validatePlan(pddlParser.getPlan());
		if (!pr.isValid) {
			System.err.println("Input plan is not valid");
			System.err.println(pr.message);
			System.exit(1);
		}

		return pddlParser;

	}

	private static List<String> getFileOptions(MktrOptions options) {

		List<String> fileOptions = new ArrayList<String>();

		if (OPTS_FILE.exists()) {
			try {
				Properties props = new Properties();
				props.load(new FileInputStream(OPTS_FILE));

				for (Object key : props.keySet()) {
					String keyStr = key.toString();
					String val = props.getProperty(keyStr);

					if (!val.equals("false")) {
						fileOptions.add("--" + keyStr);
						if (!val.equals("true"))
							fileOptions.add(val);
					}
				}
			} 
			catch (IOException e) {
				System.err.println("Error parsing " + OPTS_FILE + ": " + e.getMessage());
				if (options.verbose)
					e.printStackTrace();
				System.exit(1);
			}
		}

		return fileOptions;
	}

	private static void recursivelyDelete(File f) {
		if (!f.exists())
			return;
		if (f.isDirectory()) {
			for (File c : f.listFiles())
				recursivelyDelete(c);
		}
		if (!f.delete()) {
			System.err.println("Error deleting temp file: " + f);
		}
	}

	private static class MktrOptions {

		@Option(name = "--help", usage = "print this help message", help = true, metaVar = "OPT")
		private boolean help;

		@Option(name = "--domain", usage = "domain file", required = true)
		private File domainFile;

		@Option(name = "--problem", usage = "problem file", required = true)
		private File problemFile;

		@Option(name = "--plan", usage = "plan file", required = true)
		private File planFile;

		@Option(name = "--treewidth", usage = "maximum treewidth", required = true)
		private int treewidth;

		@Option(name = "--encoder", usage = "PC plan encoder", required = true)
		private String encoderName;
		
		@Option(name = "--policy", usage = "relaxation policy", required = true)
		private String policyName;
		
		@Option(name = "--links-per-step", usage = "number of pc links to add at each step", required = false)
		private int nPerStep = 1;
		
		@Option(name = "--mktr-time", usage = "time limit (in minutes) for running MKTR, or < 0 for no limit")
		private int mktrTime = -1;

		@Option(name = "--count-time", usage = "time limit (in minutes) for counting/generating instantiations of final partial plan, or <= 0 for no limit")
		private int planCountTime = -1;

		@Option(name = "--verbose", usage = "verbose", metaVar = "OPT")
		private boolean verbose;

		@Option(name = "--validate", usage = "validate plans", depends = "--verbose", metaVar = "OPT")
		private boolean validate;

		@Option(name = "--csp-out", usage = "final CSP output file")
		private File outCspFile = new File("csp.mzn");
		
		@Option(name = "--csp-dat-out", usage = "final serialized CSP output file")
		private File outCspDatFile = null;

		@Option(name = "--plans-out", usage = "print instantiations of the final partial plan to this file")
		private File outPlansFile = new File("plans.pddl");

		@Option(name = "--print-max", usage = "only print this many plans, or <= -1 to print them all")
		private int maxPrint = -1;

	}

	private MktrMain() { }

}
