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
package au.rmit.agtgrp.pp.partialplan.planset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import au.rmit.agtgrp.lang.fol.Substitution;
import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.PddlProblem;
import au.rmit.agtgrp.lang.pddl.Plan;
import au.rmit.agtgrp.pp.partialplan.pcplan.encoder.PcToCspEncoder;

public class PlanSubstitutionSet implements PlanSet {

	protected final PddlProblem problem;
	protected final List<Operator<Variable>> planSteps;
	
	protected final Iterable<Substitution<Constant>> subs;
	protected final int size;

	protected final Substitution<Constant> initSub;
	protected final Substitution<Constant> goalSub;

	private final Map<Operator<Variable>, Variable> opVarMap;

	public PlanSubstitutionSet(PddlProblem problem, List<Operator<Variable>> planSteps, Iterable<Substitution<Constant>> subs, int size) {

		this.problem = problem;
		this.planSteps = planSteps;
		this.subs = subs;
		this.size = size;

		if (subs.iterator().hasNext()) {
			Substitution<Constant> sub = subs.iterator().next(); 
			this.initSub = Substitution.trim(sub, planSteps.get(0).getParameters());
			this.goalSub = Substitution.trim(sub, planSteps.get(planSteps.size() - 1).getParameters());
		}
		else { //empty
			this.initSub = null;
			this.goalSub = null;
		}
		
		opVarMap = PcToCspEncoder.getOperatorVariableMap(planSteps);

	}

	public Iterator<Plan> iterator() {
		return new PartialPctPlanIterator();
	}

	@Override
	public int getPlanCount() {
		return size;
	}

	private class PartialPctPlanIterator implements Iterator<Plan> {

		private Iterator<Substitution<Constant>> subIt = subs.iterator();

		@Override
		public boolean hasNext() {
			return subIt.hasNext();
		}

		@Override
		public Plan next() {
			if (!subIt.hasNext())
				throw new NoSuchElementException();
			
			Substitution<Constant> sub = subIt.next();
			// check sub first
			for (Variable var : initSub.getVariables()) {
				if (!initSub.apply(var).equals(sub.apply(var))) {
					throw new IllegalArgumentException("Substitution does not match initial state:\n" + sub);
				}
			}

			for (Variable var : goalSub.getVariables()) {
				if (!goalSub.apply(var).equals(sub.apply(var)))
					throw new IllegalArgumentException("Substitution does not match goal state:\n" + sub);
			}

			return PlanSubstitutionSet.buildPlan(problem, planSteps, sub, opVarMap);
		}

	}


	public static Plan buildPlan(PddlProblem problem, List<Operator<Variable>> planSteps, Substitution<Constant> sub, Map<Operator<Variable>, Variable> opVarMap) {

		List<Operator<Variable>> freeSteps = new ArrayList<Operator<Variable>>(planSteps);
		Collections.sort(freeSteps, new OpComparator(freeSteps, sub, opVarMap));

		List<Operator<Constant>> groundSteps = new ArrayList<Operator<Constant>>();

		for (Operator<Variable> step : freeSteps)
			groundSteps.add(step.applySubstitution(sub));
		

		return new Plan(problem, groundSteps);

	}

	private static class OpComparator implements Comparator<Operator<Variable>> {

		Map<Operator<Variable>, Integer> orderMap;

		public OpComparator(List<Operator<Variable>> steps, Substitution<Constant> sub,
				Map<Operator<Variable>, Variable> opVarMap) {
			orderMap = new HashMap<Operator<Variable>, Integer>();

			for (Operator<Variable> step : steps) {	
				Constant ord = sub.apply(opVarMap.get(step));
				int o = Integer.valueOf(ord.getName());
				orderMap.put(step, o);
			}
		}

		@Override
		public int compare(Operator<Variable> op1, Operator<Variable> op2) {
			return Integer.compare(orderMap.get(op1), orderMap.get(op2));
		}

	}

}
