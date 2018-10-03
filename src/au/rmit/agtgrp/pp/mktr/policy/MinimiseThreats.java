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

import java.util.Collections;
import java.util.List;

import au.rmit.agtgrp.lang.pddl.pct.CausalStucture;
import au.rmit.agtgrp.lang.pddl.pct.CausalStructureFactory;
import au.rmit.agtgrp.lang.pddl.pct.PcLink;
import au.rmit.agtgrp.lang.pddl.pct.PcThreatSet;
import au.rmit.agtgrp.pp.partialplan.pcplan.PcPlan;

public class MinimiseThreats extends RelaxationPolicy {

	private final PcThreatSet threats;

	public MinimiseThreats(PcPlan pcoPlan, CausalStucture options) {
		super(pcoPlan, options);
		threats = CausalStructureFactory.getThreats(planSteps, options);
	}

	@Override
	public boolean resortEachStep() {
		return false;
	}

	@Override
	public void sort(List<PcLink> edges) {
		Collections.sort(edges, this);
	}

	@Override
	public int compare(PcLink o1, PcLink o2) {
		int c = Integer.compare(threats.getThreatsToLink(o1).size(), threats.getThreatsToLink(o2).size());
		if (c == 0)
			c = super.planOrder.compare(o1, o2);

		return c;
	}

}
