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
import java.util.List;

import au.rmit.agtgrp.lang.fol.expression.Connective;
import au.rmit.agtgrp.lang.fol.expression.Expression;
import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.lang.pddl.pct.Consumer;
import au.rmit.agtgrp.lang.pddl.pct.CausalStructureFactory;
import au.rmit.agtgrp.lang.pddl.pct.PcLink;
import au.rmit.agtgrp.lang.pddl.pct.PcThreatSet;
import au.rmit.agtgrp.lang.pddl.pct.Producer;
import au.rmit.agtgrp.lang.pddl.pct.Threat;

//a.k.a RestrictedPartialOrderEncoder
public class ModalTruthRestrictedBindings extends PcToCspEncoder {

	protected void setProducerConsumerConstraints() {

		CausalStucture constraints = plan.getConstraints();

		PcThreatSet threats = CausalStructureFactory.getThreats(plan.getPlanSteps(), plan.getConstraints());

		// for each precon of each operator
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

					Variable prodOrdinal = opVarMap.get(producer.operator);
					List<Expression<Variable>> conj = new ArrayList<Expression<Variable>>();

					// co-designation constraint
					for (int v = 0; v < consumer.literal.getAtom().getParameters().size(); v++)
						conj.add(Expression
								.buildLiteral(Literal.equals(producer.literal.getAtom().getVariables().get(v),
										consumer.literal.getAtom().getVariables().get(v), true)));

					// ordering constraint
					conj.add(Expression.buildLiteral(Literal.prec(prodOrdinal, consOrdinal)));

					// now each threat
					for (Threat threat : threats.getThreatsToLink(new PcLink(producer, consumer))) { 

						Variable threatOrd = opVarMap.get(threat.operator);
						boolean codesig = true;
						for (int v = 0; v < consumer.literal.getAtom().getParameters().size(); v++) {
							Constant cval = plan.getOriginalSub()
									.apply(consumer.literal.getAtom().getVariables().get(v));
							Constant tval = plan.getOriginalSub()
									.apply(threat.literal.getAtom().getVariables().get(v));

							if (!cval.equals(tval)) { // values must remain
														// different
								conj.add(Expression
										.buildLiteral(Literal.equals(consumer.literal.getAtom().getVariables().get(v),
												threat.literal.getAtom().getVariables().get(v), false)));
								codesig = false;
								break;
							}

						}
						if (codesig) {
							// t < p
							if (plan.getPlanSteps().indexOf(threat.operator) < plan.getPlanSteps()
									.indexOf(producer.operator))
								conj.add(Expression.buildLiteral(Literal.prec(threatOrd, prodOrdinal)));
							// c < t
							else if (plan.getPlanSteps().indexOf(threat.operator) > plan.getPlanSteps()
									.indexOf(consumer.operator))
								conj.add(Expression.buildLiteral(Literal.prec(consOrdinal, threatOrd)));
							else
								conj.add(Expression.buildLiteral(Literal.prec(threatOrd, prodOrdinal)));
						}

					}

					if (!conj.isEmpty()) // this can happen when predicate has
											// no parameters, i.e. is a
											// proposition
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

	@Override
	public boolean isTotalOrder() {
		return false;
	}

	@Override
	public boolean isGround() {
		return false;
	}

}
