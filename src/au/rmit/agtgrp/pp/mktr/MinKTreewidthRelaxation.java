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
package au.rmit.agtgrp.pp.mktr;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import au.rmit.agtgrp.csp.ExpressionCsp;
import au.rmit.agtgrp.csp.PartitionedExpressionCsp;
import au.rmit.agtgrp.csp.solver.CspSolutionSet;
import au.rmit.agtgrp.csp.solver.CspSolver;
import au.rmit.agtgrp.lang.pddl.Plan;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.PddlProblem.PlanResult;
import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.lang.pddl.pct.CausalStructureFactory;
import au.rmit.agtgrp.lang.pddl.pct.PcLink;
import au.rmit.agtgrp.pp.mktr.policy.RelaxationPolicy;
import au.rmit.agtgrp.pp.partialplan.PartialPlan;
import au.rmit.agtgrp.pp.partialplan.pcplan.PcPlan;
import au.rmit.agtgrp.pp.partialplan.pcplan.encoder.PcToCspEncoder;
import au.rmit.agtgrp.pp.partialplan.planset.PlanSet;
import au.rmit.agtgrp.pp.partialplan.planset.PlanSubstitutionSet;
import au.rmit.agtgrp.utils.FormattingUtils;
import au.rmit.agtgrp.utils.collections.graph.treewidth.TreewidthCalculator;

public class MinKTreewidthRelaxation {

	private Plan plan;

	private PcToCspEncoder cspEncoder;
	private String cspEncoderName;

	private RelaxationPolicy policy;
	private String policyName;

	private int nPerStep;

	private int maxTreewidth;
	private boolean validatePlans;
	private boolean verbose;

	private int toMinutes;

	private TreewidthCalculator twCalculator;
	private CspSolver cspSolver;

	// results
	private int nPcLinksTested;
	private int nPcLinksAdded;
	private PcPlan pcPlan;

	// used to print state
	private int prevNumPlans;
	private int prevPcPlanSize;

	private PrintStream out = System.out;

	private PartialPlan finalPartialPlan;

	/**
	 * Initialises the MKTR algorithm.
	 * 
	 * @param plan				The a valid plan.
	 * @param cspEncoder		The name of the encoder type used to translate the PC plan's causal structure into a CSP.
	 * @param policyName		The name of the policy used to select which PC link to add the PC plan at each step.
	 * @param maxTreewidth		The maximum allowable treewidth of the PC plan.
	 * @param toMinutes			The timeout.
	 * @param validatePlans		Validate the new plans found MKTR, i.e., the instantiations of the PC plan.
	 * @param verbose			Print additional informato=ion, i.e., the current treewidth and number of instantiations.
	 * @param twCalc			The treewidth calculator. 
	 * @param cspSolver			The CSP solver.
	 */
	public MinKTreewidthRelaxation(Plan plan, String cspEncoder,
			String policyName, int maxTreewidth, int linksPerStep, int toMinutes, 
			boolean validatePlans, boolean verbose,
			TreewidthCalculator twCalc, CspSolver cspSolver) {

		this.plan = plan;

		this.cspEncoderName = cspEncoder;
		this.policyName = policyName;

		this.maxTreewidth = maxTreewidth;
		this.validatePlans = validatePlans;
		this.verbose = verbose;

		if (linksPerStep <= 0)
			throw new IllegalArgumentException("links per step must be positive");

		this.nPerStep = linksPerStep;
		this.toMinutes = toMinutes;

		this.twCalculator = twCalc;
		this.cspSolver = cspSolver;
	}

	public void setOutput(PrintStream out) {
		this.out = out;
	}

	public void relax() {

		CountDownLatch latch = new CountDownLatch(1);
		Future<Void> f = Executors.newSingleThreadExecutor().submit(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				try {
					runMktr();
					latch.countDown();
					return null;
				}
				catch (Exception e) {
					latch.countDown();
					throw e;
				}			
			}
		});

		try {		
			if (toMinutes >= 0)
				f.get(toMinutes, TimeUnit.MINUTES); //time out has been set
			else
				f.get(); //wait indefinitely

		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e.getCause());
		} catch (TimeoutException e) {
			out.println("Time limit reached, waiting for MKTR to stop gracefully ...");
			f.cancel(true); //interrupt the MKTR thread
			twCalculator.cancel();
			cspSolver.cancel();

			// wait for MKTR to gracefully stop
			try {
				latch.await();
			} catch (InterruptedException e1) {
				throw new RuntimeException(e1);
			}
		}

	}


	/**
	 * Execute the MKTR algorithm.
	 */
	private void runMktr() {

		printSetup();

		// build encoder
		out.println("Initialising CSP encoder");
		cspEncoder = PcToCspEncoder.getInstance(cspEncoderName);

		// build PC plan
		out.println("Converting plan into causal structure C");
		pcPlan = CausalStructureFactory.getEquivalentPcoPlan(plan, cspEncoder.isTotalOrder());

		// build minimal causal structure
		out.println("Building minimal causal structure C_A");
		CausalStucture minimalConstraints = CausalStructureFactory.getMinimalPcoConstraints(plan, cspEncoder.isTotalOrder());		

		// build heuristic
		out.println("Initialising relaxation policy");
		policy = RelaxationPolicy.getInstance(policyName, pcPlan, minimalConstraints);

		// get all pc options
		List<PcLink> pcOptions = new ArrayList<PcLink>(minimalConstraints.getAllPcLinks());
		pcOptions.removeAll(pcPlan.getConstraints().getAllPcLinks());
		pcOptions = policy.sortAndFilter(pcOptions);

		// init data
		nPcLinksTested = 0;
		nPcLinksAdded = 0;
		prevNumPlans = 1;
		prevPcPlanSize = pcPlan.getConstraints().getAllPcLinks().size();


		printHeaders();
		try {
			printState(null, pcPlan, pcOptions, null);
		} catch (InterruptedException e) {
			// csp calculation was cancelled
			return;
		}


		//start relaxation
		while (!pcOptions.isEmpty()) {

			if (Thread.interrupted()) {
				Thread.currentThread().interrupt();
				break;
			}

			nPcLinksTested++;

			// select n edges
			List<PcLink> edges = new ArrayList<PcLink>();
			for (int i = 0; i < nPerStep && !pcOptions.isEmpty(); i++)
				edges.add(pcOptions.remove(0));

			int n = edges.size();
			while (!edges.isEmpty()) {

				if (Thread.interrupted()) {
					Thread.currentThread().interrupt();
					break;
				}

				List<PcLink> attempt = new ArrayList<PcLink>(edges.subList(0, n)); // new list to prevent co-mod exception

				// add edges to graph
				for (PcLink edge : attempt)
					pcPlan.getConstraints().addProducerConsumerOption(edge);

				// convert to CSP
				ExpressionCsp csp = cspEncoder.toCSP(pcPlan, maxTreewidth);

				// test treewidth of csp
				try {
					if (!twCalculator.isGreaterThan(csp.getPrimalGraph(), maxTreewidth)) {
						nPcLinksAdded+=attempt.size();

						// re-sort edges if necessary
						if (policy.resortEachStep())
							pcOptions = policy.sortAndFilter(pcOptions);

						// remove from current list
						edges.removeAll(attempt);

					} else { // edges created tw greater than mtw, remove from pc plan
						for (PcLink edge : attempt)
							pcPlan.getConstraints().removeProducerConsumerOption(edge);

						// this edge can be removed
						if (n == 1)
							edges.removeAll(attempt);

					}
				} catch (InterruptedException e) {
					// tw calculation was cancelled

					// remove last additions
					for (PcLink edge : attempt)
						pcPlan.getConstraints().removeProducerConsumerOption(edge);

					break;
				}

				// update console
				try {
					printState(csp, pcPlan, pcOptions, attempt);
				} catch (InterruptedException e) {
					// csp calc was cancelled
					break;
				}

				if (n > 1)
					n /= 2;

			}
		}

	}

	private void printSetup() {
		out.println("Initialising MKTR");
		out.println("Domain:    " + plan.getDomain().getName());
		out.println("Problem:   " + plan.getProblem().getName());
		out.println("Treewidth: " + maxTreewidth);
		out.println("Encoder:   " + cspEncoderName);
		out.println("Policy:    " + policyName);
		out.println("Time:      " + (toMinutes > 0 ? (toMinutes + " minute(s)"): "none"));
	}

	private void printHeaders() {
		out.println("Starting MKTR");
		if (verbose)
			out.println("#C\t#C_A \ttw\t#plans\tcnt_t\topt_t\t  producer -> consumer");
		else
			out.println("#C\t#C_A\t  producer -> consumer");
	}

	private void printState(ExpressionCsp csp, PcPlan pcPlan, List<PcLink> opts, List<PcLink> edges) throws InterruptedException {

		int pcPlanSize = pcPlan.getConstraints().getAllPcLinks().size();
		if (verbose && csp != null) {
			String nsolsStr = "?";
			int nPlans = prevNumPlans;
			int twEst = twCalculator.getUpperBound(csp.getPrimalGraph());
			//an edge was added (i.e., tw <= maxtreewidth), or this is the first iteration
			if (prevPcPlanSize != pcPlanSize || nPcLinksTested == 0) { 
				nPlans = getPlanCount(csp, -1).count;
				nsolsStr = Integer.toString(nPlans);
				if (twEst > maxTreewidth)
					twEst = maxTreewidth;
			}

			String twStr = twEst == 1 ? "1" : "<="  + twEst;
			String added = prevPcPlanSize != pcPlanSize ? "+ " : "  ";
			out.println(pcPlan.getConstraints().getAllPcLinks().size() + "\t" + opts.size() + "\t" + twStr
					+ "\t" + nsolsStr + "\t" + FormattingUtils.formatTime(cspSolver.getRuntime()) + "\t"
					+ FormattingUtils.formatTime(((double) cspEncoder.getEncodingTime())) + "\t"
					+ added
					+ (edges == null ? " " : edges.get(0)));

			if (edges != null) {
				for (int i = 1; i < edges.size(); i++)
					out.println("\t\t\t\t\t\t" + added + edges.get(i));
			}

			//if new plans were found, or it is the first iteration, validate if required
			if ((nPlans != prevNumPlans || nPcLinksTested == 0) && validatePlans) {
				out.println("Validating new plans");
				validate();
			}

			prevNumPlans = nPlans;


		} else {
			String added = prevPcPlanSize != pcPlanSize ? "+ " : "  ";
			out.println(pcPlan.getConstraints().getAllPcLinks().size() + "\t" + opts.size() + "\t"
					+ added
					+ (edges == null ? " " : edges.get(0)));

			if (edges != null) {
				for (int i = 1; i < edges.size(); i++)
					out.println("\t\t" + added + edges.get(i));
			}
		}

		prevPcPlanSize = pcPlanSize;

	}

	private MktrResult getPlanCount(ExpressionCsp csp, long timeout) throws InterruptedException {
		cspSolver.countSolutions(csp, TimeUnit.MINUTES.toMillis(timeout));
		return new MktrResult(null, cspSolver.getSolutionCount(), cspSolver.timedOut());
	}

	private void validate() throws InterruptedException {
		PlanSet partialPlan = buildPartialPlan(-1).plans;
		// validate it
		PlanResult validationResult = plan.getProblem().validateAll(partialPlan);
		if (!validationResult.isValid)
			throw new RuntimeException("\nInvalid plan found!\n" + validationResult.message);
	}

	private MktrResult buildPartialPlan(long timeout) throws InterruptedException {
		//build CSP with all diff and type ordering
		ExpressionCsp csp = cspEncoder.toCSP(pcPlan, true, true, true);
		return buildPartialPlan(csp, timeout);
	}
	
	private MktrResult buildPartialPlan(ExpressionCsp csp, long timeout) throws InterruptedException {
		cspSolver.getSolutions(csp, TimeUnit.MINUTES.toMillis(timeout));
		CspSolutionSet cspSols = cspSolver.getSolutions();
		cspSols = ((PartitionedExpressionCsp) csp).departitionSolutions(cspSols);

		PlanSet partialPlan = new PlanSubstitutionSet(pcPlan.getProblem(), pcPlan.getPlanSteps(), cspSols, cspSols.getSolutionCount());

		return new MktrResult(partialPlan, partialPlan.getPlanCount(), cspSolver.timedOut());
	}

	public int getnPcLinksTested() {
		return nPcLinksTested;
	}

	public int getnPcLinksAdded() {
		return nPcLinksAdded;
	}

	public PartialPlan getFinalPartialPlan() {
		if (finalPartialPlan == null) {
			out.println("Building final CSP");
			ExpressionCsp csp = cspEncoder.toCSP(pcPlan, true, true, true);
			finalPartialPlan = new PartialPlan(plan.getProblem(), new HashSet<Operator<Variable>>(pcPlan.getPlanSteps()), csp);
		}
		return finalPartialPlan;
	}

	public PcPlan getFinalPcPlan() {
		return pcPlan;
	}

	public MktrResult getInstantiationCount(long timeoutMinutes) throws InterruptedException {
		out.println("Counting re-instantiations");
		PartialPlan pp = getFinalPartialPlan();
		return getPlanCount(pp.getConstraints(), timeoutMinutes);
	}

	public MktrResult getReinstantiations(long timeoutMinutes) throws InterruptedException {
		out.println("Generating re-instantiations");
		PartialPlan pp = getFinalPartialPlan();
		return buildPartialPlan(pp.getConstraints(), timeoutMinutes);
	}

}
