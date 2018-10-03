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
package au.rmit.agtgrp.lang.pddl.parser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.predicate.Atom;
import au.rmit.agtgrp.lang.fol.predicate.Literal;
import au.rmit.agtgrp.lang.fol.predicate.Predicate;
import au.rmit.agtgrp.lang.fol.symbol.Term;
import au.rmit.agtgrp.lang.fol.symbol.Type;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.OperatorFactory;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.PddlDomain;
import au.rmit.agtgrp.lang.pddl.PddlProblem;
import au.rmit.agtgrp.lang.pddl.State;
import au.rmit.agtgrp.lang.utils.SymbolMap;
import fr.uga.pddl4j.parser.Connective;
import fr.uga.pddl4j.parser.Domain;
import fr.uga.pddl4j.parser.Exp;
import fr.uga.pddl4j.parser.NamedTypedList;
import fr.uga.pddl4j.parser.Op;
import fr.uga.pddl4j.parser.Problem;
import fr.uga.pddl4j.parser.RequireKey;
import fr.uga.pddl4j.parser.TypedSymbol;

public class Pddl4JTranslator {

	private static EnumSet<Connective> ALLOWED_CONNECTIVES = EnumSet.of(Connective.AND, Connective.NOT);
	private static EnumSet<RequireKey> SUPPORTED_PDDL = EnumSet.of(RequireKey.STRIPS, RequireKey.TYPING, 
			RequireKey.EQUALITY, RequireKey.NEGATIVE_PRECONDITIONS);

	public static PddlDomain convertDomain(Domain pddl4j) {

		// check requirements
		for (RequireKey req : pddl4j.getRequirements()) {
			if (!SUPPORTED_PDDL.contains(req))
				throw new UnsupportedOperationException("Requirement " + req + " is not supported");
		}

		// convert predicates
		Map<String, Predicate> predsByName = new HashMap<String, Predicate>();
		Set<Predicate> preds = new HashSet<Predicate>();

		for (NamedTypedList pred : pddl4j.getPredicates()) {
			String name = pred.getName().getImage();
			List<Type> params = new ArrayList<Type>();
			for (TypedSymbol arg : pred.getArguments()) {
				params.add(new Type(arg.getTypes().get(0).getImage()));
			}

			Predicate predSymbol = new Predicate(name, params);
			predsByName.put(name, predSymbol);
			preds.add(predSymbol);
		}

		// get function names
		List<String> functions = new ArrayList<String>();
		for (NamedTypedList func : pddl4j.getFunctions())
			functions.add(func.getName().getImage());

		// convert types
		Set<Type> types = new HashSet<Type>();

		for (TypedSymbol type : pddl4j.getTypes()) {
			Type t = new Type(type.getImage());
			types.add(t);

			if (type.getTypes().size() > 0) {
				Type superType = new Type(type.getTypes().get(0).getImage());
				t.setSupertype(superType);
			}
		}

		// convert operators
		Set<Operator<Variable>> operators = new HashSet<Operator<Variable>>();

		for (Op pddl4jOp : pddl4j.getOperators()) {

			String opName = pddl4jOp.getName().getImage();
			List<Variable> vars = new ArrayList<Variable>();

			for (TypedSymbol param : pddl4jOp.getParameters()) {
				if (pddl4j.getConstants().contains(param))
					throw new RuntimeException(
							"Constants are not supported in parameters:\n" + param + " in operator\n" + opName);

				vars.add(new Variable(new Type(param.getTypes().get(0).getImage()), param.getImage()).intern());
			}

			OperatorFactory<Variable> of = new OperatorFactory<Variable>(opName);
			of.addVariables(vars);
			of.addParameters(vars);

			for (Literal<Variable> pre : convertExpression(pddl4jOp.getPreconditions(), vars, predsByName))
				of.addPrecondition(pre);

			for (Literal<Variable> post : convertExpression(pddl4jOp.getEffects(), vars, predsByName))
				of.addPostcondition(post);

			operators.add(of.getOperator());
		}

		// convert constants
		Set<Constant> constants = new HashSet<Constant>();
		for (TypedSymbol cnst : pddl4j.getConstants()) {
			constants.add(new Constant(new Type(cnst.getTypes().get(0).getImage()), cnst.getImage()));
		}

		return new PddlDomain(pddl4j.getName().getImage(), preds, types, constants, operators);

	}

	public static List<Literal<Variable>> convertExpression(Exp exp, List<Variable> opParams,
			Map<String, Predicate> predsByName) {

		List<Literal<Variable>> lits = new ArrayList<Literal<Variable>>();

		if (exp.isLiteral()) {
			boolean val = true;

			if (exp.getConnective().equals(Connective.NOT)) {
				val = false;
				if (exp.getChildren().size() > 1)
					throw new UnsupportedOperationException("Cannot handle nested expressions" + exp);

				exp = exp.getChildren().get(0);
			}

			lits.add(new Literal<Variable>(convertAtom(exp, opParams, predsByName), val).intern());
			return lits;
		}

		if (!ALLOWED_CONNECTIVES.contains(exp.getConnective()))
			throw new UnsupportedOperationException(exp.getConnective() + " is not supported in expression:\n" + exp);

		for (Exp ch : exp.getChildren()) {

			if (!ch.isLiteral() && !ALLOWED_CONNECTIVES.contains(ch.getConnective()))
				throw new UnsupportedOperationException(ch.getConnective() + " is not supported in expression:\n" + ch);

			boolean val = true;

			if (!ch.isLiteral() && !ch.getConnective().equals(Connective.NOT))
				throw new UnsupportedOperationException("Nested expressions are not supported:\n" + exp);

			if (ch.getConnective().equals(Connective.NOT)) {
				val = false;
				if (ch.getChildren().size() > 1)
					throw new UnsupportedOperationException("Nested expressions are not supported:\n" + ch);

				ch = ch.getChildren().get(0);
			}

			lits.add(new Literal<Variable>(convertAtom(ch, opParams, predsByName), val).intern());

		}

		return lits;

	}

	private static Atom<Variable> convertAtom(Exp exp, List<Variable> opParams, Map<String, Predicate> predsByName) {

		Predicate pred = null;
		if (exp.getConnective().equals(Connective.EQUAL_ATOM)) {
			pred = Predicate.EQUALS;
		}
		else {
			String predname = exp.getAtom().get(0).getImage();
			pred = predsByName.get(predname);
		}
		
		if (pred == null)
			throw new PddlParserException("Cannot parse atom: " + exp);

		int first = exp.getConnective().equals(Connective.EQUAL_ATOM) ? 0 : 1;
		List<Variable> params = new ArrayList<Variable>();
		for (int i = first; i < exp.getAtom().size(); i++) {
			String paramName = exp.getAtom().get(i).getImage();
			boolean found = false;
			for (Variable opParam : opParams) {
				if (opParam.getName().equalsIgnoreCase(paramName)) {
					params.add(opParam);
					found = true;
				}
			}
			if (!found) {
				throw new RuntimeException(
						"Constants are not supported in preconditions:\n" + paramName + " in\n" + exp);
			}
		}

		return new Atom<Variable>(pred, params, params).intern();
	}

	public static PddlProblem convertProblem(PddlDomain domain, Problem prob, Domain pddl4jDomain) {

		SymbolMap<Predicate> sm = new SymbolMap<Predicate>(domain.getPredicates());

		// name
		String name = prob.getName().getImage();

		// objects
		Set<Constant> objs = new HashSet<Constant>();
		for (TypedSymbol obj : prob.getObjects())
			objs.add(new Constant(new Type(obj.getTypes().get(0).getImage()), obj.getImage()));

		// initial state
		Set<Literal<Constant>> initialState = new HashSet<Literal<Constant>>();

		for (Exp init : prob.getInit()) {
			Atom<Constant> i = convertGroundAtom(init, sm, prob, pddl4jDomain);
			initialState.add(new Literal<Constant>(i, true).intern());
		}

		// goal state
		Set<Literal<Constant>> goalState = new HashSet<Literal<Constant>>();
		goalState.addAll(convertGroundExpression(prob.getGoal(), sm, prob, pddl4jDomain));

		return new PddlProblem(domain, name, objs, new State<Constant>(initialState), 
				new State<Constant>(goalState));
	}

	public static List<Literal<Constant>> convertGroundExpression(Exp exp,
			Map<String, Predicate> predsByName, Problem prob, Domain pddl4jDomain) {
		List<Literal<Constant>> lits = new ArrayList<Literal<Constant>>();

		if (exp.isLiteral()) {
			boolean val = !exp.getConnective().equals(Connective.NOT);
			lits.add(new Literal<Constant>(convertGroundAtom(exp, predsByName, prob, pddl4jDomain), val).intern());
			return lits;
		}

		if (!exp.getConnective().equals(Connective.AND))
			throw new UnsupportedOperationException(
					exp.getConnective() + " is not supported in goal/init state:\n" + exp);

		for (Exp ch : exp.getChildren()) {
			if (!ch.isLiteral())
				throw new UnsupportedOperationException("Nested expressions are not supported:\n" + exp);
			boolean val = true;
			if (ch.getConnective().equals(Connective.NOT)) {
				val = false;
				if (ch.getChildren().size() > 1)
					throw new UnsupportedOperationException("Nested expressions are not supported:\n" + ch);

				ch = ch.getChildren().get(0);
			}

			lits.add(new Literal<Constant>(convertGroundAtom(ch, predsByName, prob, pddl4jDomain), val).intern());

		}

		return lits;

	}

	private static Atom<Constant> convertGroundAtom(Exp exp, Map<String, Predicate> predsByName, Problem prob, Domain domain) {
		String predname = exp.getAtom().get(0).getImage();
		Predicate pred = predsByName.get(predname);
		List<Constant> params = new ArrayList<Constant>();
		for (int i = 1; i < exp.getAtom().size(); i++) {
			TypedSymbol st = prob.getObject(exp.getAtom().get(i)); // look up declared object in problem
			if (st == null)
				st = domain.getConstant(exp.getAtom().get(i)); // not in prob, find in domain

			Type type = new Type(st.getTypes().get(0).getImage());
			String paramName = st.getImage();

			params.add(new Constant(type, paramName));
		}

		return new Atom<Constant>(pred, Variable.buildVariables(Term.getTypes(params)), params);

	}


	private Pddl4JTranslator() { }

}
