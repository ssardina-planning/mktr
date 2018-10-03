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

import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;

public class Consumer extends AbstractPct {

	public Consumer(Operator<Variable> operator, Literal<Variable> literal) {
		super(operator, literal);
	}

	@Override
	protected void verify() {
		if (!operator.getPreconditions().contains(literal))
			throw new IllegalArgumentException(literal + " is not a precondition of " + operator.getName());
	}
	
	public Consumer intern() {
		return getCached(this);
	}

}
