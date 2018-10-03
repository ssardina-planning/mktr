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
package au.rmit.agtgrp.lang.pddl.pct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import au.rmit.agtgrp.lang.fol.predicate.Atom;
import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.predicate.Predicate;
import au.rmit.agtgrp.lang.fol.symbol.Term;
import au.rmit.agtgrp.lang.fol.symbol.Type;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.Plan;
import au.rmit.agtgrp.pp.partialplan.pcplan.PcPlan;

public class CausalStructureFactory {

	public static PcPlan getEquivalentPcoPlan(Plan plan, boolean totalOrder) {
		CausalStucture constraints = getEquivalentPcoConstraints(plan, totalOrder);
		return new PcPlan(plan.getProblem(), plan.getPlanSteps(), plan.getSubstitution(), constraints);
	}

	public static PcPlan getMinimalPcoPlan(Plan plan, boolean totalOrder) {
		CausalStucture constraints = getMinimalPcoConstraints(plan, totalOrder);
		return new PcPlan(plan.getProblem(), plan.getPlanSteps(), plan.getSubstitution(), constraints);
	}

	public static CausalStucture getEquivalentPcoConstraints(Plan plan, boolean totalOrder) {

		CausalStucture constraints = new CausalStucture(totalOrder);

		for (int i = 1; i < plan.length(); i++) { // each step in plan, except initial step

			Operator<Variable> cons = plan.getPlanSteps().get(i);

			for (int pre_i = 0; pre_i < cons.getPreconditions().size(); pre_i++) { // each precon in step

				Literal<Variable> pre = cons.getPreconditions().get(pre_i);

				Producer actualProducer = null;

				// find actual producer
				for (int j = 0; j < i; j++) { // each previous step in plan, in order

					Operator<Variable> prod = plan.getPlanSteps().get(j);

					//equality, neg preconditions can link to initial state
					
					if (j == 0) {

						
						if (pre.getAtom().getSymbol().equals(Predicate.EQUALS)) { // equality precon
							Variable v1 = pre.getAtom().getVariables().get(0);
							Variable v2 = pre.getAtom().getVariables().get(1);

							// (H1 = H1) or (H1 != H2)
							if (plan.getSubstitution().apply(v1).equals(plan.getSubstitution().apply(v2)) == pre.getValue()) {

								List<Variable> newVars = new ArrayList<Variable>();
								for (Variable var : pre.getAtom().getVariables())
									newVars.add(getInitialStateVariable(var, plan));

								actualProducer = new Producer(prod, pre.resetVariables(newVars).rebind(newVars)).intern();

							}
						}
						else if (!pre.getValue()) { // neg precon, not equality 
							// pre = -p(x), p(x) is not in initial state
							
							List<Variable> newVars = new ArrayList<Variable>();
							for (Variable var : pre.getAtom().getVariables())
								newVars.add(getInitialStateVariable(var, plan));

							Literal<Variable> resetPre = pre.resetVariables(newVars).rebind(newVars);
							if (!prod.getPostconditions().contains(resetPre.getNegated()))
								actualProducer = new Producer(prod, resetPre).intern();
							
						}						
					}
					

					// each postcon in previous steps
					for (int post_j = 0; post_j < prod.getPostconditions().size(); post_j++) { 

						Literal<Variable> postcon = prod.getPostconditions().get(post_j);

						if (pre.getAtom().getSymbol().equals(postcon.getAtom().getSymbol()) && // same symbol
								pre.getValue() == postcon.getValue() && // same value
								!prod.isUndone(postcon)) { // is  not undone by later effect

							// if bindings are the same, actual link found
							boolean samebindings = true;
							for (int b = 0; b < pre.getAtom().getVariables().size(); b++) {
								Variable prev = pre.getAtom().getVariables().get(b);
								Variable postv = postcon.getAtom().getVariables().get(b);

								samebindings &= plan.getSubstitution().apply(prev).equals(plan.getSubstitution().apply(postv));
							}
							if (samebindings) {
								actualProducer = new Producer(prod, postcon).intern();
							}
						}

					}
				}
				if (actualProducer == null) {
					throw new RuntimeException("No producer found for precondition:\n" 
							+ pre + "\nIn operator: \n" + cons);
				}

				// actual pc link
				constraints.addProducerConsumerOption(actualProducer, new Consumer(cons, pre).intern());

			}
		}

		return constraints;
	}

	public static CausalStucture getMinimalPcoConstraints(Plan plan, boolean totalOrder) {

		CausalStucture constraints = new CausalStucture(totalOrder);

		for (int i = 1; i < plan.length(); i++) { // each step in plan, except initial step

			Operator<Variable> cons = plan.getPlanSteps().get(i);
			for (int pre_i = 0; pre_i < cons.getPreconditions().size(); pre_i++) { // each precon in step

				Literal<Variable> pre = cons.getPreconditions().get(pre_i);
				Consumer consPc = new Consumer(cons, pre).intern();

				// find all potential producers
				for (int j = 0; j < (totalOrder ? i : plan.length()); j++) { // po/to -- every step in plan 

					// equality, negated precons
					if (j == 0) {
						if (pre.getAtom().getSymbol().equals(Predicate.EQUALS)) {
							for (Producer eqProd : getInitialStateEqualityProducers(pre, plan))
								constraints.addProducerConsumerOption(eqProd, consPc);
						}
						else if (!pre.getValue()) {
							for (Producer negProd : getInitialStateNegationProducers(pre, plan))
								constraints.addProducerConsumerOption(negProd, consPc);
						}
					}
					
					
					if (j != i) {
						Operator<Variable> prod = plan.getPlanSteps().get(j);

						for (int post_j = 0; post_j < prod.getPostconditions().size(); post_j++) { // each postcon in other step

							Literal<Variable> post = prod.getPostconditions().get(post_j);

							Producer prodPc = new Producer(prod, post).intern();


							if (pre.getAtom().getSymbol().equals(post.getAtom().getSymbol()) && // same symbol
									pre.getValue() == post.getValue() && // same value
									assignable(post.getAtom(), pre.getAtom()) && // bindable, as per variable types
									(j == 0 || !prod.isUndone(post))) { // initial state "effects" are never undone.

								constraints.addProducerConsumerOption(prodPc, consPc);

							}
						}
					}
				}

				if (constraints.getProducers(consPc).isEmpty())
					throw new RuntimeException("No producer for consumer: " + consPc);
			}
		}

		return constraints;

	}


	private static Set<Producer> getInitialStateEqualityProducers(Literal<Variable> pre, Plan plan) {

		if (!pre.getAtom().getSymbol().equals(Predicate.EQUALS))
			throw new IllegalArgumentException(pre.toString());

		Set<Producer> prods = new HashSet<Producer>();

		Variable preVar1 = pre.getAtom().getVariables().get(0);
		Variable preVar2 = pre.getAtom().getVariables().get(1);

		for (Variable v1 : plan.getInitialAction().getVariables()) {
			if (preVar1.getType().hasSubtype(v1.getType())) {
				for (Variable v2 : plan.getInitialAction().getVariables()) {
					if (preVar2.getType().hasSubtype(v2.getType())) {					
						if (plan.getSubstitution().apply(v1).equals(plan.getSubstitution().apply(v2)) == pre.getValue()) {
							List<Variable> newVars = Arrays.asList(v1, v2);
							prods.add(new Producer(plan.getInitialAction(), pre.resetVariables(newVars).rebind(newVars)).intern());
						}
					}
				}
			}
		}

		return prods;

	}

	private static Set<Producer> getInitialStateNegationProducers(Literal<Variable> pre, Plan plan) {

		if (pre.getValue())
			throw new IllegalArgumentException(pre.toString());

		Set<Producer> prods = new HashSet<Producer>();

		for (List<Variable> vars : getAllCombs(pre.getAtom().getSymbol().getTypes(), plan.getInitialAction().getVariables())) {		
			Literal<Variable> negEff = pre.resetVariables(vars).rebind(vars);
			if (!plan.getInitialAction().getPostconditions().contains(negEff.getNegated()))
				prods.add(new Producer(plan.getInitialAction(), negEff).intern());
			
		}
		
		return prods;

	}


	private static List<List<Variable>> getAllCombs(List<Type> types, List<Variable> initVars) {
					
		List<List<Variable>> varLists = new ArrayList<List<Variable>>();
		if (types.isEmpty()) {
			varLists.add(new ArrayList<Variable>()); // a single empty list
			return varLists;
		}
			
		List<List<Variable>> recLists = getAllCombs(types.subList(1, types.size()), initVars);
		
		Type t = types.get(0);
		
		for (Variable var : initVars) {
			if (t.hasSubtype(var.getType())) {
				for (List<Variable> recList : recLists) {			
					List<Variable> l = new ArrayList<Variable>();
					l.add(var);
					l.addAll(recList);
					varLists.add(l);
				}
			}
		}
		
		return varLists;
		
	}
	

	private static Variable getInitialStateVariable(Variable var, Plan plan) {
		for (Variable initVar : plan.getInitialAction().getVariables()) {
			if (plan.getSubstitution().apply(initVar).equals(plan.getSubstitution().apply(var))) {
				return initVar;
			}			
		}

		return null;
	}

	private static boolean assignable(Atom<? extends Term> prod, Atom<? extends Term> cons) {
		for (int i = 0; i < prod.getParameters().size(); i++) {
			if (!cons.getParameters().get(i).getType().hasSubtype(prod.getParameters().get(i).getType())
					&& !prod.getParameters().get(i).getType().hasSubtype(cons.getParameters().get(i).getType())) {
				return false;
			}
		}

		return true;
	}

	public static PcThreatSet getThreats(List<Operator<Variable>> freeSteps, CausalStucture constraints) {

		Map<Predicate, Set<PcLink>> posPcLinkMap = new HashMap<Predicate, Set<PcLink>>();
		Map<Predicate, Set<PcLink>> negPcLinkMap = new HashMap<Predicate, Set<PcLink>>();

		for (PcLink pcLink : constraints.getAllPcLinks()) {
			Predicate pred = pcLink.getProducer().literal.getAtom().getSymbol();
			Map<Predicate, Set<PcLink>> pcLinkMap = pcLink.getProducer().literal.getValue() ? posPcLinkMap
					: negPcLinkMap;
			Set<PcLink> links = pcLinkMap.get(pred);
			if (links == null) {
				links = new HashSet<PcLink>();
				pcLinkMap.put(pred, links);
			}
			links.add(pcLink);
		}

		PcThreatSet threatMap = new PcThreatSet();

		for (int i = 0; i < freeSteps.size(); i++) {
			Operator<Variable> threatOp = freeSteps.get(i);
			for (int j = 0; j < threatOp.getPostconditions().size(); j++) {
				Literal<Variable> threat = threatOp.getPostconditions().get(j);
				Set<PcLink> links = threat.getValue() ? negPcLinkMap.get(threat.getAtom().getSymbol())
						: posPcLinkMap.get(threat.getAtom().getSymbol());
				if (links != null && (i == 0 || !threatOp.isUndone(threat))) {// init state  effects cannot be undone

					for (PcLink threatened : links) { // threats for undone producers

						if (threatened.getProducer().operator.equals(threatOp) && threatened.getProducer().operator
								.getPostconditions().indexOf(threatened.getProducer().literal) > j) {

							continue;
						}

						int tIndex = i;
						int cIndex = freeSteps.indexOf(threatened.getConsumer().operator);

						if (constraints.isTotalOrder()) {
							int pIndex = freeSteps.indexOf(threatened.getProducer().operator);
							if (tIndex >= pIndex && tIndex < cIndex)
								threatMap.addThreat(threatened, new Threat(threatOp, threat.getNegated()).intern());
						} else {
							if (tIndex != cIndex) {
								threatMap.addThreat(threatened, new Threat(threatOp, threat.getNegated()).intern());
							}
						}
					}
				}
			}
		}

		return threatMap;

	}

	private CausalStructureFactory() { }

}
