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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Term;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.lang.pddl.pct.PcLink;
import au.rmit.agtgrp.pp.partialplan.pcplan.PcPlan;

public abstract class RelaxationPolicy implements Comparator<PcLink> {

	@SuppressWarnings("unchecked")
	public static RelaxationPolicy getInstance(String typeName, PcPlan pcoPlan, CausalStucture options) {
		String clsName = RelaxationPolicy.class.getPackage().getName() + "." + typeName;
		try {		
			return getInstance((Class<? extends RelaxationPolicy>) Class.forName(clsName), pcoPlan, options);
		} catch (ClassNotFoundException e) {
			throw new RelaxationPolicyException("Error loading relaxation policy: " + clsName + " not found");
		}
	}

	public static RelaxationPolicy getInstance(Class<? extends RelaxationPolicy> type, PcPlan pcoPlan, CausalStucture options) {
		try {
			Constructor<? extends RelaxationPolicy> cons = type.getConstructor(PcPlan.class, CausalStucture.class);
			return cons.newInstance(pcoPlan, options);
		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException e) {

			throw new RelaxationPolicyException("Error constructing relaxation policy " + type + ": " 
				+ e.getCause().getClass() + ", " + e.getCause().getMessage());
		}
	}

	protected final PcPlan pcoPlan;
	protected final CausalStucture options;
	protected final CausalStucture current;
	protected List<Operator<Variable>> planSteps;

	protected PlanOrderComparator planOrder;

	public RelaxationPolicy(PcPlan pcoPlan, CausalStucture options) {
		this.pcoPlan = pcoPlan;
		planSteps = pcoPlan.getPlanSteps();
		this.options = options;
		current = pcoPlan.getConstraints();
		this.planOrder = new PlanOrderComparator();
	}

	public final String getName() {
		return this.getClass().getSimpleName();
	}

	public abstract boolean resortEachStep();

	public List<PcLink> sortAndFilter(List<PcLink> edges) {
		edges = filter(edges);
		sort(edges);
		return edges;
	}

	protected void sort(List<PcLink> edges) {
		Collections.sort(edges, this);
	}

	protected List<PcLink> filter(List<PcLink> edges) {
		return edges;
	}

	protected class PlanOrderComparator implements Comparator<PcLink> {

		private int compareLiterals(Literal<? extends Term> l0, Literal<? extends Term> l1) {

			int c = l0.getAtom().getSymbol().getName().compareTo(l1.getAtom().getSymbol().getName());
			if (c == 0)
				c = Boolean.compare(l0.getValue(), l1.getValue());
			if (c == 0) {
				for (int i = 0; i < l0.getAtom().getSymbol().getArity(); i++) {
					Variable v1 = l0.getAtom().getVariables().get(i);
					Variable v2 = l1.getAtom().getVariables().get(i);
					c = v1.getName().compareTo(v2.getName());
					if (c != 0)
						return c;
				}
			}
			return c;
		}

		@Override
		// sort by producer index, consumer index, then predicate name
		public int compare(PcLink o1, PcLink o2) {
			if (o1 == o2)
				return 0;

			// operator indexes
			int pi1 = planSteps.indexOf(o1.getProducer().operator);
			int pi2 = planSteps.indexOf(o2.getProducer().operator);
			int c = Integer.compare(pi1, pi2);

			if (c == 0) {
				int ci1 = planSteps.indexOf(o1.getConsumer().operator);
				int ci2 = planSteps.indexOf(o2.getConsumer().operator);
				c = Integer.compare(ci1, ci2);
			}

			// pre/postcon indexes
			if (c == 0)
				c = compareLiterals(o1.getProducer().literal, o2.getProducer().literal);

			if (c == 0)
				c = compareLiterals(o1.getConsumer().literal, o2.getConsumer().literal);

			if (c == 0)
				throw new RuntimeException("Incomparable: " + o1.toString() + ", " + o2.toString());

			return c;
		}
	}

}
