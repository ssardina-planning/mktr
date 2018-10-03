package au.rmit.agtgrp.pp.partialplan;

import java.util.Set;

import au.rmit.agtgrp.csp.ExpressionCsp;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.PddlProblem;
import au.rmit.agtgrp.lang.pddl.Plan;
import au.rmit.agtgrp.lang.pddl.PlanFactory;
import au.rmit.agtgrp.utils.FormattingUtils;

public class PartialPlan {

	private final PddlProblem problem;
	private final Set<Operator<Variable>> operators;
	private final ExpressionCsp constraints;
	
	public PartialPlan(PddlProblem problem, Set<Operator<Variable>> operators, ExpressionCsp constraints) {
		
		if (problem == null || operators == null || constraints == null)
			throw new NullPointerException("Arguments cannot be null");
		
		this.problem = problem;
		this.operators = operators;
		this.constraints = constraints;	
			
		boolean init = false;
		boolean goal = false;
		for (Operator<Variable> op : operators) {
			if (op.getName().equals(Plan.GOAL_OP_NAME))
				goal = true;
			else if (op.getName().equals(Plan.INIT_OP_NAME))
				init = true;
		}
		
		if (!init || !goal)
			throw new IllegalArgumentException("No init or goal operator");
				
			
		if (!PlanFactory.hasUniqueVariableNames(operators)) {
			StringBuilder sb = new StringBuilder();
			for (Operator<Variable> step : operators) 
				sb.append(step.getName() + "(" + FormattingUtils.toString(step.getVariables(), ",") + ")\n");
			
			throw new IllegalArgumentException("Operator/variable name error:\n" + sb.toString());
		}
				
	}
	
	public PddlProblem getProblem() {
		return problem;
	}

	public Set<Operator<Variable>> getOperators() {
		return operators;
	}

	public ExpressionCsp getConstraints() {
		return constraints;
	}
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((constraints == null) ? 0 : constraints.hashCode());
		result = prime * result + ((operators == null) ? 0 : operators.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PartialPlan other = (PartialPlan) obj;
		if (constraints == null) {
			if (other.constraints != null)
				return false;
		} else if (!constraints.equals(other.constraints))
			return false;
		if (operators == null) {
			if (other.operators != null)
				return false;
		} else if (!operators.equals(other.operators))
			return false;
		return true;
	}

}
