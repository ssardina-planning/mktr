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
package au.rmit.agtgrp.pp.mktr.policy;

import java.util.List;

import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Term;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.lang.pddl.pct.Consumer;
import au.rmit.agtgrp.lang.pddl.pct.CausalStructureFactory;
import au.rmit.agtgrp.lang.pddl.pct.PcLink;
import au.rmit.agtgrp.lang.pddl.pct.PcThreatSet;
import au.rmit.agtgrp.lang.pddl.pct.Producer;
import au.rmit.agtgrp.lang.pddl.pct.Threat;
import au.rmit.agtgrp.pp.partialplan.pcplan.PcPlan;
import au.rmit.agtgrp.utils.collections.graph.DirectedGraph;

public class RelaxNonConcurrency extends RelaxationPolicy {

	private DirectedGraph<Operator<? extends Term>> ncr;

	public RelaxNonConcurrency(PcPlan pcoPlan, CausalStucture options) {
		super(pcoPlan, options);

		ncr = new DirectedGraph<Operator<? extends Term>>();

		PcThreatSet threats = CausalStructureFactory.getThreats(planSteps, options);

		for (PcLink pcLink : options.getAllPcLinks()) {
			Producer prod = pcLink.getProducer();
			Consumer cons = pcLink.getConsumer();

			if (codesignated(prod.literal, cons.literal) && precedes(prod.operator, cons.operator))
				ncr.addEdge(prod.operator, cons.operator);

			for (Threat threat : threats.getThreatsToLink(pcLink)) {
				if (codesignated(prod.literal, threat.literal) && precedes(prod.operator, threat.operator))
					ncr.addEdge(prod.operator, threat.operator);

				if (codesignated(cons.literal, threat.literal) && precedes(cons.operator, threat.operator))
					ncr.addEdge(cons.operator, threat.operator);

			}
		}
	}

	protected boolean precedes(Operator<Variable> pc1, Operator<Variable> pc2) {
		return pcoPlan.getPlanSteps().indexOf(pc1) < pcoPlan.getPlanSteps().indexOf(pc2);
	}

	protected boolean codesignated(Literal<Variable> pc1, Literal<Variable> pc2) {

		List<Variable> vars1 = pc1.getAtom().getVariables();
		List<Variable> vars2 = pc2.getAtom().getVariables();
		for (int i = 0; i < vars1.size(); i++) {
			Constant c1 = super.pcoPlan.getOriginalSub().apply(vars1.get(i));
			Constant c2 = super.pcoPlan.getOriginalSub().apply(vars2.get(i));

			if (!c1.equals(c2))
				return false;
		}

		return true;
	}
	

	@Override
	public boolean resortEachStep() {
		return false;
	}

	@Override
	public int compare(PcLink o1, PcLink o2) {
		int n1 = ncr.getEdgesFrom(o1.getConsumer().operator).size() + ncr.getEdgesTo(o1.getConsumer().operator).size();
		if (n1 == 0)
			n1 = Integer.MAX_VALUE;

		int n2 = ncr.getEdgesFrom(o2.getConsumer().operator).size() + ncr.getEdgesTo(o2.getConsumer().operator).size();
		if (n2 == 0)
			n2 = Integer.MAX_VALUE;

		int c = Integer.compare(n1, n2); // smallest non-zero first

		if (c == 0)
			c = super.planOrder.compare(o1, o2);

		return c;
	}

}
