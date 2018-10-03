package au.rmit.agtgrp.pp.prf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import au.rmit.agtgrp.csp.ExpressionCsp;
import au.rmit.agtgrp.lang.fol.expression.Expression;
import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Type;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.Plan;
import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.lang.pddl.pct.Consumer;
import au.rmit.agtgrp.lang.pddl.pct.CausalStructureFactory;
import au.rmit.agtgrp.lang.pddl.pct.PcLink;
import au.rmit.agtgrp.lang.pddl.pct.PcThreatSet;
import au.rmit.agtgrp.lang.pddl.pct.Producer;
import au.rmit.agtgrp.lang.pddl.pct.Threat;
import au.rmit.agtgrp.pp.partialplan.PartialPlan;

public class Prf {

	private final Plan plan;
	private PartialPlan partialPlan;

	public Prf(Plan plan) {
		this.plan = plan;
	}

	public void deorder() {
		CausalStucture constraints = CausalStructureFactory.getEquivalentPcoConstraints(plan, false);
		PcThreatSet threats = CausalStructureFactory.getThreats(plan.getPlanSteps(), constraints);

		ExpressionCsp csp = new ExpressionCsp();

		// add parameters
		csp.addVariables(plan.getSubstitution().getVariables());

		// create a variable for each operator
		Map<Operator<Variable>, Variable> opVarMap = new HashMap<Operator<Variable>, Variable>();
		for (Operator<Variable> op : plan.getPlanSteps()) {
			Variable opVar = new Variable(Type.OPERATOR_TYPE, op.getName()).intern();
			opVarMap.put(op, opVar);
			csp.addVariable(opVar);
		}

		// set var domains
		for (Variable var : plan.getSubstitution().getVariables())
			csp.addDomainValue(var, plan.getSubstitution().apply(var));

		// set operator domains
		List<Constant> ordinalDomain = new ArrayList<Constant>();
		for (int i = 0; i < plan.getPlanSteps().size(); i++)
			ordinalDomain.add(new Constant(Type.OPERATOR_TYPE, Integer.toString(i)));

		List<Constant> midCons = ordinalDomain.subList(1, ordinalDomain.size() - 1);
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			Variable opVar = opVarMap.get(plan.getPlanSteps().get(i));

			if (i == 0 || i == plan.getPlanSteps().size() - 1) //init/goal
				csp.addDomainValue(opVar, ordinalDomain.get(i));
			else 
				csp.addDomainValues(opVar, midCons);
			
		}

		//set prod-cons ordering constraints
		for (Operator<Variable> consOp : plan.getPlanSteps()) {

			// for each consumer
			for (Literal<Variable> consLit : consOp.getPreconditions()) {

				Consumer consumer = new Consumer(consOp, consLit).intern();
				Variable consOrdinal = opVarMap.get(consumer.operator);

				// can only be one producer for each consumer
				if (constraints.getProducers(consumer).isEmpty())
					throw new RuntimeException("No producer for consumer: " + consumer);

				for (Producer producer : constraints.getProducers(consumer)) {

					Variable prodOrdinal = opVarMap.get(producer.operator);

					// must be co-designated
					if (!plan.getSubstitution().apply(consumer.literal.getAtom().getVariables())
							.equals(plan.getSubstitution().apply(producer.literal.getAtom().getVariables())))					
						continue;

					// if prod < cons in original plan, must be retained
					if (plan.getPlanSteps().indexOf(producer.operator) < plan.getPlanSteps().indexOf(consumer.operator))
						csp.addConstraint(Expression.buildLiteral(Literal.prec(prodOrdinal, consOrdinal)));

					if (plan.getPlanSteps().indexOf(consumer.operator) < plan.getPlanSteps().indexOf(producer.operator))
						csp.addConstraint(Expression.buildLiteral(Literal.prec(consOrdinal, prodOrdinal)));

					// now each threat
					for (Threat threat : threats.getThreatsToLink(new PcLink(producer, consumer))) { 

						if (!plan.getSubstitution().apply(consumer.literal.getAtom().getVariables())
								.equals(plan.getSubstitution().apply(threat.literal.getAtom().getVariables())))					
							continue;

						Variable threatOrdinal = opVarMap.get(threat.operator);

						// if prod < threat in plan, must be retained
						if (plan.getPlanSteps().indexOf(producer.operator) < plan.getPlanSteps().indexOf(threat.operator))
							csp.addConstraint(Expression.buildLiteral(Literal.prec(prodOrdinal, threatOrdinal)));

						// if threat < prod, must be retained
						if (plan.getPlanSteps().indexOf(threat.operator) < plan.getPlanSteps().indexOf(producer.operator))
							csp.addConstraint(Expression.buildLiteral(Literal.prec(threatOrdinal, prodOrdinal)));

						// if cons < threat in plan, must be retained
						if (plan.getPlanSteps().indexOf(consumer.operator) < plan.getPlanSteps().indexOf(threat.operator))
							csp.addConstraint(Expression.buildLiteral(Literal.prec(consOrdinal, threatOrdinal)));

						// if threat < cons in plan, must be retained
						if (plan.getPlanSteps().indexOf(threat.operator) < plan.getPlanSteps().indexOf(consumer.operator))
							csp.addConstraint(Expression.buildLiteral(Literal.prec(threatOrdinal, consOrdinal)));

					}
				}
			}
		}
		
		partialPlan = new PartialPlan(plan.getProblem(), new HashSet<Operator<Variable>>(plan.getPlanSteps()), csp);

	}

	public PartialPlan getDeorderedPlan() {
		if (partialPlan == null)
			throw new IllegalStateException("Has not been de-ordered yet");
		return partialPlan;
	}


}
