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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import au.rmit.agtgrp.lang.fol.Substitution;
import au.rmit.agtgrp.lang.fol.function.Constant;
import au.rmit.agtgrp.lang.fol.symbol.Variable;
import au.rmit.agtgrp.lang.pddl.Operator;
import au.rmit.agtgrp.lang.pddl.Plan;
import au.rmit.agtgrp.lang.pddl.PlanFactory;
import au.rmit.agtgrp.lang.pddl.PddlDomain;
import au.rmit.agtgrp.lang.pddl.PddlProblem;
import au.rmit.agtgrp.lang.utils.SymbolMap;
import fr.uga.pddl4j.parser.Parser;

public class PddlParser {

	private PddlDomain domain;
	private PddlProblem problem;
	private Plan plan;

	public void parse(File domainFile, File problemFile) throws FileNotFoundException {

		Parser parser = new Parser();
		
		try {
			
			parser.parseDomain(domainFile.getAbsolutePath());
			if (!parser.getErrorManager().isEmpty()) {
				parser.getErrorManager().printAll();
				throw new PddlParserException("Error parsing PDDL in " + domainFile);
			}

			parser.parse(domainFile, problemFile);
			if (!parser.getErrorManager().isEmpty()) {
				parser.getErrorManager().printAll();
				throw new PddlParserException("Error parsing PDDL in " + problemFile);
			}
			
		}
		catch (FileNotFoundException e) {
			throw new PddlParserException(e.getMessage());
		}

		domain = Pddl4JTranslator.convertDomain(parser.getDomain());
		problem = Pddl4JTranslator.convertProblem(domain, parser.getProblem(), parser.getDomain());
		plan = null;
	}



	public void setDomainAndProblem(PddlProblem problem) {
		this.domain = problem.getDomain();
		this.problem = problem;
		plan = null;
	}

	public void parseFFPlan(File planFile) throws IOException, PddlParserException {
		List<String> planStrs = Files.readAllLines(Paths.get(planFile.toURI()));
		planStrs.remove(0);

		List<String[]> stepStrings = new ArrayList<String[]>();
		for (String line : planStrs) {
			if (!line.trim().isEmpty()) { // watch out for blank lines
				line = line.substring(line.indexOf('(') + 1, line.indexOf(')'));
				stepStrings.add(line.split(" "));
			}
		}

		buildPlan(stepStrings);
	}

	public void parseFDPlan(File planFile) throws IOException, PddlParserException {
		parseFDPlan(Files.readAllLines(Paths.get(planFile.toURI())));
	}

	public void parseFDPlan(List<String> planStrs) throws IOException, PddlParserException {

		List<String[]> stepStrings = new ArrayList<String[]>();
		for (String line : planStrs) {
			if (!line.trim().isEmpty() && !line.contains(";")) { // watch out for blank lines, e.g., ;cost =
				line = line.substring(line.indexOf('(') + 1, line.indexOf(')'));
				stepStrings.add(line.split(" "));
			}
		}

		buildPlan(stepStrings);
	}

	private void buildPlan(List<String[]> stepStrings) throws PddlParserException {

		if (domain == null || problem == null)
			throw new PddlParserException("Must parse domain and problem before plan.");
		
		List<Operator<Constant>> planSteps = new ArrayList<Operator<Constant>>();

		SymbolMap<Operator<Variable>> opMap = new SymbolMap<Operator<Variable>>(domain.getOperators());
		SymbolMap<Constant> objMap = new SymbolMap<Constant>(problem.getObjects());
		SymbolMap<Constant> constsMap = new SymbolMap<Constant>(domain.getConstants());

		for (String[] stepStr : stepStrings) {
			Operator<Variable> op = opMap.get(stepStr[0]);

			if (op == null) {
				throw new PddlParserException("Unknown operator: " + stepStr[0]);
			}
			List<Constant> params = new ArrayList<Constant>();
			for (int i = 1; i < stepStr.length; i++) {
				Constant ob = objMap.get(stepStr[i]);   //objects in problem
				if (ob == null) 						//constants in domain
					ob = constsMap.get(stepStr[i]);
				if (ob == null)
					throw new PddlParserException("Unknown object: " + stepStr[i]);

				params.add(ob);
			}

			
			planSteps.add(op.applySubstitution(Substitution.build(op.getVariables(), params)));
		}
		
		plan = PlanFactory.formatAsPlan(problem, planSteps, false, false);
	}

	public PddlDomain getDomain() {
		return domain;
	}

	public PddlProblem getProblem() {
		return problem;
	}

	public Plan getPlan() {
		return plan;
	}

}
