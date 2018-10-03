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

import au.rmit.agtgrp.lang.fol.expression.Expression;
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

public class PrfEncoder extends GroundEncoder {

	@Override
	protected void setProducerConsumerConstraints() {
		CausalStucture constraints = plan.getConstraints();
		PcThreatSet threats = CausalStructureFactory.getThreats(plan.getPlanSteps(), plan.getConstraints());

		// prod, cons -> prod # cons, cons # prod
		// prod, threat -> prod # threat, threat # prod
		// cons, threat -> cons # threat, threat # cons

		// for each each operator
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
					if (!codesignated(consumer.literal.getAtom().getVariables(),
							producer.literal.getAtom().getVariables()))
						continue;

					// if prod < cons in original plan, must be retained
					if (plan.getPlanSteps().indexOf(producer.operator) < plan.getPlanSteps().indexOf(consumer.operator))
						csp.addConstraint(Expression.buildLiteral(Literal.prec(prodOrdinal, consOrdinal)));

					if (plan.getPlanSteps().indexOf(consumer.operator) < plan.getPlanSteps().indexOf(producer.operator))
						csp.addConstraint(Expression.buildLiteral(Literal.prec(consOrdinal, prodOrdinal)));

					// now each threat
					for (Threat threat : threats.getThreatsToLink(new PcLink(producer, consumer))) { 

						// must be co-designated
						if (!codesignated(consumer.literal.getAtom().getVariables(),
								threat.literal.getAtom().getVariables()))
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
	}

}
