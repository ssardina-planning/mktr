package au.rmit.agtgrp.pp.kk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import au.rmit.agtgrp.csp.ExpressionCsp;
import au.rmit.agtgrp.lang.fol.expression.Connective;
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
import au.rmit.agtgrp.pp.partialplan.pcplan.encoder.PcToCspEncoder;
import au.rmit.agtgrp.pp.partialplan.pcplan.optimiser.CspOptimiser;
import au.rmit.agtgrp.utils.collections.Pair;
import au.rmit.agtgrp.utils.collections.graph.DirectedGraph;
import au.rmit.agtgrp.utils.collections.graph.GraphUtils;

public class KkGeneraliser {

	private final Plan plan;
	private PartialPlan partialPlan;
	
	public KkGeneraliser(Plan plan) {
		this.plan = plan;
	}

	public void generalise(boolean optimise) {
		
		// explanation
		CausalStucture expl = CausalStructureFactory.getEquivalentPcoConstraints(plan, false);
		PcThreatSet threats = CausalStructureFactory.getThreats(plan.getPlanSteps(), expl);

		ExpressionCsp csp = new ExpressionCsp();
		
		// add parameter vars
		csp.addVariables(plan.getSubstitution().getVariables());

		// create a variable for each operator
		Map<Operator<Variable>, Variable>	opVarMap = new HashMap<Operator<Variable>, Variable>();
		for (Operator<Variable> op : plan.getPlanSteps()) {
			Variable opVar = new Variable(Type.OPERATOR_TYPE, op.getName()).intern();
			opVarMap.put(op, opVar);
			csp.addVariable(opVar);
		}

		// get ordering 
		DirectedGraph<Operator<Variable>> justified = setOrderingConstraints(csp, expl, threats, opVarMap);
		
		// add constraints
		for (Pair<Operator<Variable>, Operator<Variable>> edge : justified.getAllEdges())
			csp.addConstraint(Expression.buildLiteral(Literal.prec(opVarMap.get(edge.getFirst()), opVarMap.get(edge.getSecond()))));

		
		// co/non-co-designation
		setDesignationConstraints(csp, expl, threats, justified);
		
		// set domains
		setDomains(csp, opVarMap);

		// add this to prevent duplicate solutions
		PcToCspEncoder.addAllDifferent(csp);
		PcToCspEncoder.setOperatorTypeOrdering(csp, plan.getPlanSteps(), opVarMap);
		
		// optimise
		if (optimise)
			csp = CspOptimiser.optimise(csp);
		
		partialPlan = new PartialPlan(plan.getProblem(), new HashSet<Operator<Variable>>(plan.getPlanSteps()), csp);
		
	}
	
	public PartialPlan getGeneralisedPlan() {
		return partialPlan;
	}

	protected void setDomains(ExpressionCsp csp, Map<Operator<Variable>, Variable> opVarMap) {

		Set<Constant> objects = new HashSet<Constant>(plan.getDomain().getConstants());
		objects.addAll(plan.getProblem().getObjects());

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
		for (Type t : plan.getDomain().getTypes())
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
				if (plan.getInitialAction().getVariables().contains(var))
					csp.addDomainValue(var, plan.getSubstitution().apply(var));
				else if (plan.getGoalAction().getVariables().contains(var))
					csp.addDomainValue(var, plan.getSubstitution().apply(var));
				else {
					for (Type t : objsByType.keySet()) {
						if (var.getType().hasSubtype(t))
							csp.addDomainValues(var, objsByType.get(t));
					}
				}
			}
		}
	}

	private void setDesignationConstraints(ExpressionCsp csp, CausalStucture expl, PcThreatSet threats, DirectedGraph<Operator<Variable>> order) {

		//e-conditions
		Map<Operator<Variable>, List<PcLink>> econs = new HashMap<Operator<Variable>, List<PcLink>>();
		for (int i = 1; i < plan.getPlanSteps().size(); i++) { //not initial action!
			
			Operator<Variable> step = plan.getPlanSteps().get(i);

			List<PcLink> es = new ArrayList<PcLink>();
			for (Literal<Variable> eff : step.getPostconditions()) {
				Producer prod = new Producer(step, eff).intern();
				for (Consumer cons : expl.getConsumers(prod))
					es.add(new PcLink(prod, cons));

			}

			econs.put(step, es);
		}
		//System.out.println("*** PCONS");
		//p-conditions
		Map<Threat, List<PcLink>> pcons = new HashMap<Threat, List<PcLink>>();
		for (Operator<Variable> step : plan.getPlanSteps()) {

			for (Literal<Variable> eff : step.getPostconditions()) { //every effect
				Threat thrt = new Threat(step, eff.getNegated()).intern();
				
				List<PcLink> ps = new ArrayList<PcLink>();
				//System.out.println(thrt);
				for (PcLink thrtnd : threats.getLinksThreatenedByProducer(thrt)) { // every link thrtnd by this
					//System.out.println("    " + thrtnd);
					if (!order.containsEdge(thrt.operator, thrtnd.getProducer().operator) && //possible that p < t < c 
						!order.containsEdge(thrtnd.getConsumer().operator, thrt.operator)) {
						
						//System.out.println("        " + thrt);
						ps.add(thrtnd);
					}
				}

				pcons.put(thrt, ps);
			}

		}

		//System.out.println("*** CODESIGNATION");
		// co-designation
		for (Operator<Variable> step : plan.getPlanSteps()) {
			if (step.equals(plan.getInitialAction()))
				continue;
			
			//producer = consumer
			for (PcLink econ : econs.get(step)) {
				//System.out.println(econ);
				for (int i = 0; i < econ.getProducer().literal.getAtom().getSymbol().getArity(); i++) {
					csp.addConstraint(Expression.buildLiteral(Literal.equals(econ.getConsumer().literal.getAtom().getVariables().get(i), 
							econ.getProducer().literal.getAtom().getVariables().get(i), true)));
				}				
			}
		}

		//System.out.println("*** NON-CODESIGNATION");
		// non-co-designation
		for (Threat thrt : pcons.keySet()) {
			//System.out.println(thrt);
			for (PcLink pcon : pcons.get(thrt)) {
				//System.out.println("    " + pcon);
				List<Literal<Variable>> noncos = new ArrayList<Literal<Variable>>();			
				for (int i = 0; i < thrt.literal.getAtom().getSymbol().getArity(); i++) {
					noncos.add(Literal.equals(thrt.literal.getAtom().getVariables().get(i), 
							pcon.getConsumer().literal.getAtom().getVariables().get(i), false));
				}

				csp.addConstraint(Expression.buildExpressionFromLiterals(Connective.OR, noncos));
			}
		}

		// build initial state "preconditions"
		CausalStucture allLinks = CausalStructureFactory.getMinimalPcoConstraints(plan, false);

		Operator<Variable> init = plan.getInitialAction();
		for (Literal<Variable> eff : init.getPostconditions()) { // all effects of init

			for (Consumer consumer : expl.getConsumers(init, eff)) { // consumes init
				List<Expression<Variable>> disjs = new ArrayList<Expression<Variable>>();

				for (Producer producer : allLinks.getProducers(consumer)) { // all potential producers for consumer
					if (producer.operator.equals(init)) { // producers in init state
						List<Literal<Variable>> conjs = new ArrayList<Literal<Variable>>();
						for (int i = 0; i < eff.getAtom().getSymbol().getArity(); i++) {
							conjs.add(Literal.equals(consumer.literal.getAtom().getVariables().get(i), 
									producer.literal.getAtom().getVariables().get(i), true));

						}

						disjs.add(Expression.buildExpressionFromLiterals(Connective.AND, conjs));

					}
				}
				csp.addConstraint(Expression.buildExpression(Connective.OR, disjs));
			}
		}
	}
	
	
	private DirectedGraph<Operator<Variable>> setOrderingConstraints(ExpressionCsp csp, CausalStucture expl, PcThreatSet threats, Map<Operator<Variable>, Variable> opVarMap) {
		DirectedGraph<Operator<Variable>> ordering = new DirectedGraph<Operator<Variable>>();
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			for (int j = i+1; j < plan.getPlanSteps().size(); j++)
				ordering.addEdge(plan.getPlanSteps().get(i), plan.getPlanSteps().get(j));
		}

		Set<Pair<Operator<Variable>, Operator<Variable>>> justified = new HashSet<Pair<Operator<Variable>, Operator<Variable>>>();
		for (Pair<Operator<Variable>, Operator<Variable>> edge : ordering.getAllEdges()) {
			if (edge.getFirst().equals(plan.getInitialAction()) || 
					edge.getSecond().equals(plan.getGoalAction()) || 
					justified(edge, expl, threats))

				justified.add(edge);
		}
		
		DirectedGraph<Operator<Variable>> minimised = new DirectedGraph<Operator<Variable>>();
		for (Pair<Operator<Variable>, Operator<Variable>> edge : justified) {
			//System.out.println(edge.getFirst().getName() + " < " + edge.getSecond().getName());
			GraphUtils.addAndCloseTransitive(minimised, edge.getFirst(), edge.getSecond());
		}
		
		return minimised;
		
		
	}

	private boolean justified(Pair<Operator<Variable>, Operator<Variable>> ord, CausalStucture expl, PcThreatSet threats) {
		// prod -> con link
		for (Literal<Variable> eff : ord.getFirst().getPostconditions()) { //every postcon
			for (Consumer cons : expl.getConsumers(ord.getFirst(), eff)) { //every consumer of this postcon
				if (cons.operator.equals(ord.getSecond()))
					return true;
			}

		}

		// first is a consumer, second is threat to first, first and second are co-des		
		for (Literal<Variable> pre : ord.getFirst().getPreconditions()) {
			Consumer cons = new Consumer(ord.getFirst(), pre).intern();
			for (Producer prod : expl.getProducers(cons)) { //all producers for first
				PcLink thrtnd = new PcLink(prod, cons);
				for (Threat thrt : threats.getThreatsToLink(thrtnd)) {
					if (thrt.operator.equals(ord.getSecond()) &&
						plan.getSubstitution().apply(thrt.literal.getAtom().getVariables())
							.equals(plan.getSubstitution().apply(pre.getAtom().getVariables())))
						return true;
				}
			}

		}

		// second is producer, first is threat to it, first is threat to consumer
		for (Literal<Variable> eff : ord.getSecond().getPostconditions()) {
			Producer prod = new Producer(ord.getSecond(), eff).intern();
			for (Consumer cons : expl.getConsumers(prod)) { //all consumers
				PcLink thrtnd = new PcLink(prod, cons);
				for (Threat thrt : threats.getThreatsToLink(thrtnd)) {
					if (thrt.operator.equals(ord.getFirst()) && 
						plan.getSubstitution().apply(thrt.literal.getAtom().getVariables())
						.equals(plan.getSubstitution().apply(cons.literal.getAtom().getVariables())))
						
						return true;
				}
			}
		}

		return false;

	}

}
