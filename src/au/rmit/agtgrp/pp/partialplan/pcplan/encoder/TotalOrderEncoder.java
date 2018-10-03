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

import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.symbol.Type;
import au.rmit.agtgrp.lang.fol.symbol.Variable;

public class TotalOrderEncoder extends ModalTruthEncoder {

	protected void setProducerConsumerConstraints() {

		super.setProducerConsumerConstraints();

		// set domain of each operator
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			Variable opVar = opVarMap.get(plan.getPlanSteps().get(i));
			csp.getDomain(opVar).clear();
			csp.addDomainValue(opVar, new Constant(Type.OPERATOR_TYPE, Integer.toString(i)));
		}

	}

	@Override
	public boolean isTotalOrder() {
		return true;
	}

}
