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
package au.rmit.agtgrp.pp.partialplan.pcplan.encoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import au.rmit.agtgrp.csp.ExpressionCsp;
import au.rmit.agtgrp.csp.PartitionedExpressionCsp;
import au.rmit.agtgrp.csp.alldiff.AllDifferent;
import au.rmit.agtgrp.lang.fol.expression.Expression;
import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Type;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.pp.partialplan.pcplan.PcPlan;
import au.rmit.agtgrp.pp.partialplan.pcplan.optimiser.CspOptimiser;
import au.rmit.agtgrp.utils.collections.graph.treewidth.TreewidthCalculator;

public abstract class PcToCspEncoder {

	
	public static Map<Operator<Variable>, Variable> getOperatorVariableMap(List<Operator<Variable>> steps) {
		Map<Operator<Variable>, Variable> opVarMap = new HashMap<Operator<Variable>, Variable>();
		for (Operator<Variable> op : steps) {
			Variable opVar = new Variable(Type.OPERATOR_TYPE, op.getName()).intern();
			opVarMap.put(op, opVar);
		}
		
		return opVarMap;
	}
	
	@SuppressWarnings("unchecked")
	public static PcToCspEncoder getInstance(String encoderName) {
		String clsName = PcToCspEncoder.class.getPackage().getName()  + "." + encoderName;
		try {		
			return getInstance((Class<? extends PcToCspEncoder>) Class.forName(clsName));
		} catch (ClassNotFoundException e) {
			throw new PcToCspEncoderException("Error loading CSP encoder: " + clsName + " not found");
		}
	}

	public static PcToCspEncoder getInstance(Class<? extends PcToCspEncoder> encClass) {
		try {
			return encClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new PcToCspEncoderException("Error loading csp encoder: " + encClass + ": " + e.getMessage());
		}
	}

	public static void addAllDifferent(ExpressionCsp csp) {

		// find all operator variables
		List<Variable> opVars = new ArrayList<Variable>();
		for (Variable var : csp.getVariables()) {
			if (var.getType().equals(Type.OPERATOR_TYPE))
				opVars.add(var);
		}

		// build all diff literal
		Literal<Variable> allDiffLit = AllDifferent.buildAllDifferent(opVars);

		// add constraint
		csp.addConstraint(Expression.buildLiteral(allDiffLit));
	}

	public static void removeAllDifferent(ExpressionCsp csp) {
		for (List<Variable> dom : csp.getConstraints().keySet()) {
			for (Expression<Variable> cons : csp.getConstraints(dom)) {
				if (cons.isLiteral() && AllDifferent.isAllDifferentLiteral(cons.getLiteral())) {
					csp.getConstraints(dom).remove(cons);
					return;
				}
			}
		}
	}
	
	public static void setOperatorTypeOrdering(ExpressionCsp csp, List<Operator<Variable>> steps, Map<Operator<Variable>, Variable> opVarMap) {

		Map<String, List<Variable>> opsByType = new HashMap<String, List<Variable>>();

		for (Operator<Variable> op : steps) {
			String type = op.getName();
			List<Variable> ops = opsByType.get(type);
			if (ops == null) {
				ops = new ArrayList<Variable>();
				opsByType.put(type, ops);
			}
			ops.add(opVarMap.get(op));
		}

		for (String type : opsByType.keySet()) {
			List<Variable> ops = opsByType.get(type);
			for (int i = 0; i < ops.size() - 1; i++)
				csp.addConstraint(Expression.buildLiteral(Literal.prec(ops.get(i), ops.get(i + 1))));
		}

	}

	protected long time = 0;

	protected Set<Constant> objects;
	protected Set<Type> types;
	protected PcPlan plan;

	protected ExpressionCsp csp;
	protected Map<Operator<Variable>, Variable> opVarMap;

	public abstract boolean isTotalOrder();

	public abstract boolean isGround();

	public String getName() {
		return this.getClass().getSimpleName();
	}

	public ExpressionCsp toCSP(PcPlan plan) {
		return toCSP(plan, true, false, false, -1);
	}

	public ExpressionCsp toCSP(PcPlan plan, int targetTw) {
		return toCSP(plan, true, false, false, targetTw);
	}

	public ExpressionCsp toCSP(PcPlan plan, boolean optimise, boolean allDiff) {
		return toCSP(plan, optimise, allDiff, false, -1);
	}

	public ExpressionCsp toCSP(PcPlan plan, boolean optimise, boolean allDiff, boolean typeOrder) {
		return toCSP(plan, optimise, allDiff, typeOrder, -1);
	}

	public ExpressionCsp toCSP(PcPlan plan, boolean optimise, boolean allDiff, int targetTw) {
		return toCSP(plan, optimise, allDiff, false, targetTw);
	}

	public ExpressionCsp toCSP(PcPlan plan, boolean optimise, boolean allDiff, boolean typeOrder, int targetTw) {

		Set<Constant> objects = new HashSet<Constant>();
		objects.addAll(plan.getDomain().getConstants());
		objects.addAll(plan.getProblem().getObjects());

		Set<Type> types = new HashSet<Type>(plan.getDomain().getTypes());

		return toCSP(plan, objects, types, optimise, allDiff, typeOrder, targetTw, null);
	}

	public ExpressionCsp toCSP(PcPlan plan, Set<Constant> objects, Set<Type> types, boolean optimise, boolean allDiff,
			boolean typeOrder, int targetTw, TreewidthCalculator twc) {

		time = System.currentTimeMillis();
		this.objects = objects;
		this.types = types;
		this.plan = plan;

		csp = new ExpressionCsp();

		// add variables from preconditions etc
		for (Operator<Variable> op : plan.getPlanSteps())
			csp.addVariables(op.getParameters());

		// create a variable for each operator
		opVarMap = getOperatorVariableMap(plan.getPlanSteps());
		csp.addVariables(opVarMap.values());

		setDomains();

		setProducerConsumerConstraints();

		if (typeOrder)
			setOperatorTypeOrdering(csp, plan.getPlanSteps(), opVarMap);

		if (optimise)
			csp = CspOptimiser.optimise(csp, targetTw, twc);

		if (allDiff) {
			Literal<Variable> allDiffLit = AllDifferent.buildAllDifferent(new ArrayList<Variable>(opVarMap.values()));
			if (csp instanceof PartitionedExpressionCsp) {
				PartitionedExpressionCsp pcsp = (PartitionedExpressionCsp) csp;
				allDiffLit = allDiffLit.applySubstitution(pcsp.getMapping());
			}

			csp.addConstraint(Expression.buildLiteral(allDiffLit));
		}

		time = System.currentTimeMillis() - time;

		return csp;

	}

	public long getEncodingTime() {
		return time;
	}



	protected void setDomains() {

		// set global domain from planning problem
		csp.addDomainValues(objects);

		// set global domain from operator positions
		List<Constant> ordinalDomain = new ArrayList<Constant>();
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			ordinalDomain.add(new Constant(Type.OPERATOR_TYPE, Integer.toString(i)));
		}
		csp.addDomainValues(ordinalDomain);

		// get types
		Map<Type, Set<Constant>> objsByType = new HashMap<Type, Set<Constant>>();
		for (Type t : types)
			objsByType.put(t, new HashSet<Constant>());

		// get objs from problem
		for (Constant obj : objects)
			objsByType.get(obj.getType()).add(obj);

		// add domains for each variable
		List<Constant> midCons = ordinalDomain.subList(1, ordinalDomain.size() - 1);
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			Variable opVar = opVarMap.get(plan.getPlanSteps().get(i));
			csp.addDomainValue(opVar, ordinalDomain.get(i));
			if (i != 0 && i != plan.getPlanSteps().size() - 1)
				csp.addDomainValues(opVar, midCons);

		}

		for (Variable var : csp.getVariables()) {
			if (!var.getType().equals(Type.OPERATOR_TYPE)) {
				if (plan.getInitAction().getVariables().contains(var))
					csp.addDomainValue(var, plan.getOriginalSub().apply(var));
				else if (plan.getGoalAction().getVariables().contains(var))
					csp.addDomainValue(var, plan.getOriginalSub().apply(var));
				else {
					for (Type t : objsByType.keySet()) {
						if (var.getType().hasSubtype(t))
							csp.addDomainValues(var, objsByType.get(t));
					}
				}
			}
		}
	}

	
	
	protected abstract void setProducerConsumerConstraints();

	
	protected boolean codesignated(List<Variable> vars1, List<Variable> vars2) {
		for (int i = 0; i < vars1.size(); i++) {
			Constant c1 = plan.getOriginalSub().apply(vars1.get(i));
			Constant c2 = plan.getOriginalSub().apply(vars2.get(i));

			if (!c1.equals(c2))
				return false;
		}

		return true;
	}

}
