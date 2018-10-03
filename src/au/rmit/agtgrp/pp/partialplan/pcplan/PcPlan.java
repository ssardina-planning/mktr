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
package au.rmit.agtgrp.pp.partialplan.pcplan;

import java.util.List;

import au.rmit.agtgrp.lang.fol.Substitution;
import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.PddlDomain;
import au.rmit.agtgrp.lang.pddl.PddlProblem;
import au.rmit.agtgrp.lang.pddl.Plan;
import au.rmit.agtgrp.lang.pddl.PlanFactory;
import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.utils.FormattingUtils;

public class PcPlan {

	protected final PddlProblem problem;
	protected final List<Operator<Variable>> planSteps;
	protected final Substitution<Constant> originalSub;
	protected final CausalStucture constraints;

	public PcPlan(PddlProblem problem, List<Operator<Variable>> planSteps, Substitution<Constant> originalSub, CausalStucture constraints) {

		if (problem == null || planSteps == null || originalSub == null || constraints == null)
			throw new NullPointerException("Arguments cannot be null");
		
		boolean init = false;
		boolean goal = false;
		for (Operator<Variable> op : planSteps) {
			if (op.getName().equals(Plan.GOAL_OP_NAME))
				goal = true;
			else if (op.getName().equals(Plan.INIT_OP_NAME))
				init = true;
		}
		
		if (!init || !goal)
			throw new IllegalArgumentException("No init or goal operator");
				
			
		if (!PlanFactory.hasUniqueVariableNames(planSteps)) {
			StringBuilder sb = new StringBuilder();
			for (Operator<Variable> step : planSteps) 
				sb.append(step.getName() + "(" + FormattingUtils.toString(step.getVariables(), ",") + ")\n");
			
			throw new IllegalArgumentException("Operator/variable name error:\n" + sb.toString());
		}
		
		this.problem = problem;
		this.planSteps = planSteps;
		this.originalSub = originalSub;
		this.constraints = constraints;
		
		
	}

	public PddlProblem getProblem() {
		return problem;
	}
	
	public PddlDomain getDomain() {
		return problem.getDomain();
	}
	
	public CausalStucture getConstraints() {
		return constraints;
	}

	public List<Operator<Variable>> getPlanSteps() {
		return planSteps;
	}
	
	public Operator<Variable> getInitAction() {
		return planSteps.get(0);
	}

	public Operator<Variable> getGoalAction() {
		return planSteps.get(planSteps.size()-1);
	}

	
	public Substitution<Constant> getOriginalSub() {
		return originalSub;
	}

}
