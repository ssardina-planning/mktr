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
package au.rmit.agtgrp.lang.pddl.pct;

import java.util.HashSet;
import java.util.Set;

import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.utils.FormattingUtils;
import au.rmit.agtgrp.utils.collections.graph.DirectedBipartiteGraph;

public class CausalStucture {

	private final DirectedBipartiteGraph<Producer, Consumer> producerConsumerGraph;
	private final Set<PcLink> allPcLinks;
	private boolean totalOrder;

	public CausalStucture(boolean totalOrder) {

		this.totalOrder = totalOrder;

		producerConsumerGraph = new DirectedBipartiteGraph<Producer, Consumer>();

		allPcLinks = new HashSet<PcLink>();

	}

	public Set<Consumer> getAllConsumers() {
		Set<Consumer> consumers = new HashSet<Consumer>();
		for (Consumer cons : producerConsumerGraph.getDestinationVertices())
			if (!producerConsumerGraph.getEdgesTo(cons).isEmpty())
				consumers.add(cons);

		return consumers;
	}

	public Set<Producer> getAllProducers() {
		Set<Producer> producers = new HashSet<Producer>();
		for (Producer prd : producerConsumerGraph.getSourceVertices())
			if (!producerConsumerGraph.getEdgesFrom(prd).isEmpty())
				producers.add(prd);

		return producers;
	}

	public void addProducerConsumerOption(PcLink link) {
		addProducerConsumerOption(link.getProducer(), link.getConsumer());
	}

	public void addProducerConsumerOption(Operator<Variable> prodOp, Literal<Variable> prodLit,
			Operator<Variable> consOp, Literal<Variable> consLit) {

		addProducerConsumerOption(new Producer(prodOp, prodLit), new Consumer(consOp, consLit));
	}

	public void addProducerConsumerOption(Producer producer, Consumer consumer) {
		producerConsumerGraph.addEdge(producer, consumer);
		allPcLinks.add(new PcLink(producer, consumer));
	}

	public void removeProducerConsumerOption(PcLink link) {
		removeProducerConsumerOption(link.getProducer(), link.getConsumer());
	}

	public void removeProducerConsumerOption(Operator<Variable> prodOp, Literal<Variable> prodLit,
			Operator<Variable> consOp, Literal<Variable> consLit) {

		removeProducerConsumerOption(new Producer(prodOp, prodLit).intern(), new Consumer(consOp, consLit).intern());
	}

	public void removeProducerConsumerOption(Producer producer, Consumer consumer) {
		producerConsumerGraph.removeEdge(producer, consumer);
		allPcLinks.remove(new PcLink(producer, consumer));
	}

	public Set<Producer> getProducers(Operator<Variable> consOp, Literal<Variable> consLit) {
		return getProducers(new Consumer(consOp, consLit).intern());
	}

	public Set<Producer> getProducers(Consumer consumer) {
		return producerConsumerGraph.getEdgesTo(consumer);
	}

	public Set<Consumer> getConsumers(Operator<Variable> prodOp, Literal<Variable> prodLit) {
		return getConsumers(new Producer(prodOp, prodLit).intern());
	}

	public Set<Consumer> getConsumers(Producer producer) {
		return producerConsumerGraph.getEdgesFrom(producer);
	}

	public Set<PcLink> getAllPcLinks() {
		return allPcLinks;
	}

	public boolean isTotalOrder() {
		return totalOrder;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		for (Consumer consumer : producerConsumerGraph.getDestinationVertices())
			sb.append( consumer + " = { " + 
					FormattingUtils.toString(producerConsumerGraph.getEdgesTo(consumer)) + " }\n");

		return sb.toString();
	}

}
