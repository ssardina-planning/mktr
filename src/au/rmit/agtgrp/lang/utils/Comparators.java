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
package au.rmit.agtgrp.lang.utils;

import java.util.Comparator;

import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.symbol.Symbol;
import au.rmit.agtgrp.lang.fol.symbol.SymbolInstance;

public class Comparators {

	public static final Comparator<Symbol> SYMBOL_COMPARATOR = new Comparator<Symbol>() {
		@Override
		public int compare(Symbol o1, Symbol o2) {
			return o1.getName().compareTo(o2.getName());
		}
	};
	
	public static final Comparator<SymbolInstance<?, ?>> INSTANCE_COMPARATOR = new Comparator<SymbolInstance<?, ?>>() {
		@Override
		public int compare(SymbolInstance<?, ?> o1, SymbolInstance<?, ?> o2) {
			return SYMBOL_COMPARATOR.compare(o1.getSymbol(), o2.getSymbol());
		}	
	};

	
	public static final Comparator<Literal<?>> LITERAL_COMPARATOR = new Comparator<Literal<?>>() {
		@Override
		public int compare(Literal<?> o1, Literal<?> o2) {
			int c = INSTANCE_COMPARATOR.compare(o1.getAtom(), o2.getAtom());
			if (c == 0)
				c = Boolean.compare(o1.getValue(), o2.getValue());
			
			return c;
		}		
	};

	private Comparators() { }

}
