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
import java.util.Arrays;
import java.util.List;

import au.rmit.agtgrp.lang.fol.expression.Connective;
import au.rmit.agtgrp.lang.fol.expression.Expression;
import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Type;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.lang.pddl.pct.Consumer;
import au.rmit.agtgrp.lang.pddl.pct.CausalStructureFactory;
import au.rmit.agtgrp.lang.pddl.pct.PcLink;
import au.rmit.agtgrp.lang.pddl.pct.PcThreatSet;
import au.rmit.agtgrp.lang.pddl.pct.Producer;
import au.rmit.agtgrp.lang.pddl.pct.Threat;

public class GroundEncoder extends PcToCspEncoder {

	@Override
	public boolean isTotalOrder() {
		return false;
	}

	@Override
	public boolean isGround() {
		return true;
	}

	@Override
	protected void setDomains() {
		// set global domain from planning problem
		csp.addDomainValues(objects);

		// set global domain from operator positions
		List<Constant> ordinalDomain = new ArrayList<Constant>();
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			ordinalDomain.add(new Constant(Type.OPERATOR_TYPE, Integer.toString(i)));
		}
		csp.addDomainValues(ordinalDomain);

		// domains for operator variables
		List<Constant> midCons = ordinalDomain.subList(1, ordinalDomain.size() - 1);
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			Variable opVar = opVarMap.get(plan.getPlanSteps().get(i));
			csp.addDomainValue(opVar, ordinalDomain.get(i));
			if (i != 0 && i != plan.getPlanSteps().size() - 1)
				csp.addDomainValues(opVar, midCons);

		}

		// all vars must be bound to their original values in the plan
		for (Variable var : csp.getVariables()) {
			if (!var.getType().equals(Type.OPERATOR_TYPE)) {
				csp.addDomainValues(var, Arrays.asList(plan.getOriginalSub().apply(var)));
			}
		}

	}

	@Override
	protected void setProducerConsumerConstraints() {
		CausalStucture constraints = plan.getConstraints();

		PcThreatSet threats = CausalStructureFactory.getThreats(plan.getPlanSteps(), plan.getConstraints());

		// for each each operator
		for (Operator<Variable> consOp : plan.getPlanSteps()) {

			// for each consumer
			for (Literal<Variable> consLit : consOp.getPreconditions()) {

				Consumer consumer = new Consumer(consOp, consLit).intern();

				if (constraints.getProducers(consumer).isEmpty())
					throw new RuntimeException("No producer for consumer: " + consumer);

				Variable consOrdinal = opVarMap.get(consumer.operator);

				// add producer-consumer constraints
				List<Expression<Variable>> pcOptions = new ArrayList<Expression<Variable>>();

				for (Producer producer : constraints.getProducers(consumer)) {

					// check for co-designation
					if (!codesignated(consumer.literal.getAtom().getVariables(),
							producer.literal.getAtom().getVariables())) {
						continue;
					}

					Variable prodOrdinal = opVarMap.get(producer.operator);
					List<Expression<Variable>> conj = new ArrayList<Expression<Variable>>();

					// prod < cons
					conj.add(Expression.buildLiteral(Literal.prec(prodOrdinal, consOrdinal, prodOrdinal, consOrdinal)));

					// now each threat
					for (Threat threat : threats.getThreatsToLink(new PcLink(producer, consumer))) { 

						// check for codesignation
						if (!codesignated(consumer.literal.getAtom().getVariables(),
								threat.literal.getAtom().getVariables())) {
							continue;
						}

						// t < p or c <= t
						List<Expression<Variable>> disj = new ArrayList<Expression<Variable>>();
						Variable threatOrd = opVarMap.get(threat.operator);
						disj.add(Expression.buildLiteral(Literal.prec(consOrdinal, threatOrd, consOrdinal, threatOrd)));
						disj.add(Expression.buildLiteral(Literal.prec(threatOrd, prodOrdinal, threatOrd, prodOrdinal)));

						conj.add(Expression.buildExpression(Connective.OR, disj));

					}

					if (!conj.isEmpty()) // this can happen when predicate has no parameters, i.e. is a proposition
						pcOptions.add(Expression.buildExpression(Connective.AND, conj));
					else if (!consumer.literal.getAtom().getParameters().isEmpty())
						throw new RuntimeException("No constraints for consumer " + consumer);

				}

				if (!pcOptions.isEmpty()) {
					Expression<Variable> possBindings = Expression.buildExpression(Connective.OR, pcOptions);
					csp.addConstraint(possBindings);
				} else if (!consumer.literal.getAtom().getParameters().isEmpty())
					throw new RuntimeException("No constraints for consumer " + consumer);

			}
		}

	}

}
