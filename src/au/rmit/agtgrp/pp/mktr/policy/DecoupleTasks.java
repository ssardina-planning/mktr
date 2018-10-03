package au.rmit.agtgrp.pp.mktr.policy;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.lang.pddl.pct.Consumer;
import au.rmit.agtgrp.lang.pddl.pct.PcLink;
import au.rmit.agtgrp.pp.partialplan.pcplan.PcPlan;

public class DecoupleTasks extends RelaxationPolicy {

	private static final Pattern TASK_PATTERN = Pattern.compile("(i)(\\d*)(.*)");
	private static final int INIT_TASK = -1;
	private static final int GOAL_TASK = Integer.MAX_VALUE;
	
	
	private final RelaxProducers relaxProd;
	private final Map<Operator<Variable>, Integer> opTasks;
	private final Map<Consumer, Integer> originalProducerTasks;
	
	public DecoupleTasks(PcPlan pcoPlan, CausalStucture options) {
		super(pcoPlan, options);
		
		opTasks = new HashMap<Operator<Variable>, Integer>();
		for (Operator<Variable> op : pcoPlan.getPlanSteps()) {
			if (op.equals(pcoPlan.getInitAction()))
				opTasks.put(op, INIT_TASK);
			else if (op.equals(pcoPlan.getGoalAction()))
				opTasks.put(op, GOAL_TASK);
			else
				opTasks.put(op, getTask(op.applySubstitution(pcoPlan.getOriginalSub())));
		}	
		
		
		originalProducerTasks = new HashMap<Consumer, Integer>();
		for (PcLink link : pcoPlan.getConstraints().getAllPcLinks())
			originalProducerTasks.put(link.getConsumer(), opTasks.get(link.getProducer().operator));
		
		relaxProd = new RelaxProducers(pcoPlan, options);
		
	}

	private int getTask(Operator<Constant> op) {
		Matcher matcher = TASK_PATTERN.matcher(op.getName());
		matcher.find();
		return Integer.valueOf(matcher.group(2));
	}
	
	@Override
	public boolean resortEachStep() {
		return relaxProd.resortEachStep();
	}
	
	@Override
	public int compare(PcLink o1, PcLink o2) {
		int c = 0;
				
		// prefer links to consumers which weren't originally linked to init
		int consTask1 = opTasks.get(o1.getConsumer().operator);
		int consTask2 = opTasks.get(o2.getConsumer().operator);
		int orig1 = originalProducerTasks.get(o1.getConsumer());
		int orig2 = originalProducerTasks.get(o2.getConsumer());
				
		c = Boolean.compare(orig1 == INIT_TASK || orig1 == consTask1, 
							orig2 == INIT_TASK || orig2 == consTask2);
		
		if (c != 0)
			return c;
		
		// prefer links from init
		c = Integer.compare(opTasks.get(o1.getProducer().operator), opTasks.get(o2.getProducer().operator));
		if (c != 0)
			return c;

		
		// defer to relax prod policy
		return relaxProd.compare(o1, o2);
		
	}	

}
