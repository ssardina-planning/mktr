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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

public class RelaxProducers2 extends RelaxationPolicy {

	private final Map<String, Integer> origProducerCount;
	
	private final Map<String, Integer> producerCount;
	private final Map<String, Integer> consumerCount;
	private final Map<String, Integer> maxThreatConsCount;
	private final Map<String, Integer> maxThreatenedConsCount;

	public RelaxProducers2(PcPlan pcoPlan, CausalStucture options) {
		super(pcoPlan, options);

		consumerCount = new HashMap<String, Integer>();
		
		for (Operator<Variable> op : planSteps) {
			Set<Operator<? extends Term>> consOps = new HashSet<Operator<? extends Term>>();

			for (Literal<Variable> prod : op.getPostconditions()) {
				for (Consumer cons : current.getConsumers(op, prod))
					consOps.add(cons.operator);

			}

			consumerCount.put(op.getName(), consOps.size());

		}

		PcThreatSet threats = CausalStructureFactory.getThreats(planSteps, current);
		maxThreatConsCount = new HashMap<String, Integer>();
		for (Operator<Variable> op : planSteps) {
			int max = 0;
			for (Literal<Variable> pre : op.getPreconditions()) {
				Consumer cons = new Consumer(op, pre).intern();
				for (Producer prod : current.getProducers(cons)) {
					for (Threat threat : threats.getThreatsToLink(new PcLink(prod, cons)))
						max = Math.max(consumerCount.get(threat.operator.getName()), max);
				}
			}

			maxThreatConsCount.put(op.getName(), max);
		}

		maxThreatenedConsCount = new HashMap<String, Integer>();

		for (Operator<Variable> op : planSteps) {
			int max = 0;
			for (Literal<Variable> post : op.getPostconditions()) {
				Threat prod = new Threat(op, post.getNegated()).intern();
				for (PcLink thrtnd : threats.getLinksThreatenedByProducer(prod))
					max = Math.max(consumerCount.get(thrtnd.getConsumer().operator.getName()), max);

			}

			maxThreatenedConsCount.put(op.getName(), max);

		}
		origProducerCount = new HashMap<String, Integer>();
		for (Operator<Variable> op : planSteps) {
			Set<Operator<? extends Term>> prodOps = new HashSet<Operator<? extends Term>>();

			for (Literal<Variable> cons : op.getPreconditions()) {
				for (Producer prod : current.getProducers(op, cons))
					prodOps.add(prod.operator);

			}

			origProducerCount.put(op.getName(), prodOps.size());

		}
		
		producerCount = new HashMap<String, Integer>();
		buildProducerCount();

	}

	private void buildProducerCount() {
		producerCount.clear();
		
		for (Operator<Variable> op : planSteps) {
			Set<Operator<? extends Term>> prodOps = new HashSet<Operator<? extends Term>>();

			for (Literal<Variable> cons : op.getPreconditions()) {
				for (Producer prod : current.getProducers(op, cons))
					prodOps.add(prod.operator);

			}

			producerCount.put(op.getName(), prodOps.size() - origProducerCount.get(op.getName()));

		}
		
	}
	
	@Override
	protected void sort(List<PcLink> edges) {
		buildProducerCount();
		super.sort(edges);
	}
	
	@Override
	public boolean resortEachStep() {
		return true;
	}

	@Override
	public int compare(PcLink o1, PcLink o2) {

		int cc1 = consumerCount.get(o1.getConsumer().operator.getName());
		int cc2 = consumerCount.get(o2.getConsumer().operator.getName());

		int tb1 = maxThreatConsCount.get(o1.getConsumer().operator.getName());
		int tb2 = maxThreatConsCount.get(o2.getConsumer().operator.getName());

		int c = -Integer.compare(Math.max(tb1, cc1), Math.max(tb2, cc2));

		
		if (c == 0)
			c = -Integer.compare(producerCount.get(o1.getProducer().operator.getName()), 
								producerCount.get(o2.getProducer().operator.getName()));
	
		if (c == 0)
			c = super.planOrder.compare(o1, o2);
		
		return c;

	}

}
