# MKTR: Minimal k-Treewidth Relaxation

MKTR is an application for finding *re-instantiations* of a plan, i.e., alternative plans which achieve the same goal and use the same actions, but differ in the order of those actions and how the variables in those actions have been bound.

A set of re-instantiations can be compactly represented as a *constraint formula*, i.e., a formula expressed in a fragment of first-order logic, where each model represents an alternative to the original plan.
While computing a model of a constraint formula is an NP-complete problem, if the formula's *treewidth* is bounded by `k`, a model can be found in time `O(n^k)`.

MKTR first generates a constraint formula with just one model, the original plan.
It then iteratively and greedily relaxes this formula, while keeping its treewidth below an input value.
When the formula cannot be relaxed any further without its treewidth exceeding the input value, the final constraint formula is returned.

In this program, constraint formulae are implemented as constraint satisfaction problems (CSPs).
The output to the MKTR is a CSP of bounded treewidth, each solution to which represents a different re-instantiation of the input plan.

## Dependencies

Download the following .jar files to the `lib` directory:

* [`args4j-2.33.jar`](https://github.com/kohsuke/args4j)
* [`libtw.jar`](http://www.treewidth.com/treewidth/)
* [`pddl4j-3.5.0.jar`](https://github.com/pellierd/pddl4j)

The following programs must be installed:

* [Minizinc](http://www.minizinc.org)
* [Gecode flatzinc interpreter](http://www.gecode.org/flatzinc.html)
* [treewidth-exact](https://github.com/TCS-Meiji/treewidth-exact)
* Java SDK 1.8
* [ant](http://ant.apache.org)

The programs `tw-exact`, `mzn2fzn` and`fzn-gecode` must be in the `PATH`.

## Compiling MKTR

From the root directory run:
```
ant clean-build
```
MKTR will be built in the `mktr-0.1` directory.


## Running MKTR

Navigate to the `mktr-0.1` directory, and run MKTR with the following command:

```
usage: ./mktr.sh --domain DOMAIN --problem PROBLEM 
				 --plan PLAN --treewidth TREEWIDTH
				 --encoder CSP_ENCODER --policy RELAXATION_POLICY
			  	 [--csp-out CSP_FILE] [--plans-out PLANS_FILE]
			  	 [--mktr-time MKTR_TIME] [--count-time COUNT_TIME]
			  	 [--print-max MAX_PLANS] [--verbose] [--validate] 
			  	 
```
Required arguments:

* `--domain DOMAIN`: The location of the PDDL domain file.
* `--problem PROBLEM`: The location of the problem instance PDDL file.
* `--plan PLAN`: The location of the plan which solves `PROBLEM`.
* `--treewidth TREEWIDTH`: The maximum allowable treewidth for the CSP.
* `--encoder CSP_ENCODER`: The encoder used to convert the partial plan into a CSP. Options are: 
	* `ModalTruthEncoder` 
	* `ModalTruthRestrictedOrder` 
	* `ModalTruthRestrictedBindings`
* `--policy RELAXATION_POLICY`:  The relaxation policy. Options are: 
	* `RelaxProducers` 
	* `MinimiseThreats` 
	* `Random`
	
Options:

* `--csp-out CSP_FILE`: The final CSP will be printed to this file. Defaults to `csp.mzn`.
* `--plans-out PLANS_FILE`: All instantiations of the final partial plan will be printed here. Defaults to `plans.pddl`.
* `--mktr-time MKTR_TIME`: The maximum time (in minutes) to spend running MKTR. Defaults to `-1` (no time limit).
* `--count-time COUNT_TIME`: The maximum time (in minutes) to spend counting or generating the instantiations of the final partial plan. Defaults to `-1` (no time limit).
* `--print-max MAX_PLANS`: Print no more than `MAX_PLANS` to `PLANS_FILE`. When set to 0, the instantiations will only be counted, not generated, which may result in quicker execution. Defaults to `-1` (print all).
* `--verbose`: Verbose output. At each step, the current treewidth of the partial plan, and the number of plans which instantiate it are printed. Defaults to `false`).
* `--validate` Validate new plans as they are found (requires verbose mode). Defaults to `false`.


### Specifying arguments from a file

Alternatively, command line arguments can be placed in the file `mktr.props` in the following format:

```
domain=/path/to/domain.pddl
problem=/path/to/problem.pddl
plan=/path/to/plan.pddl
verbose=true
...
```

Any arguments supplied on the command line will override those supplied in `mktr.props`.

### Example

```
./mktr.sh --domain pddl/child-snack/domain.pddl --problem pddl/child-snack/p01.pddl
		  --plan pddl/child-snack/p01.pddl.fd --treewidth 2
		  --encoder ModalTruthRestrictedOrder --policy RelaxProducers
		  --mktr-time 1 --count-time 1 --print-max 1000 --verbose
			  	 
```

## Supported PDDL fragments

All features of basic `STRIPS` are supported, except for constants appearing in preconditions or effects of operators. MKTR also supports `typing` but does not support any `ADL` features, or `equality`. 

Some of the domains and problems in the included `pddl` directory (i.e., `child-snack`, `parking`, `pegsol`, `scanalyzer` and `sokoban`) have been edited in order to meet these requirements.


## Contact

Max Waters (max.waters@rmit.edu.au).
 

## License

This project is using the GPLv3 for open source licensing for information and the license visit GNU website (https://www.gnu.org/licenses/gpl-3.0.en.html).

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see http://www.gnu.org/licenses/.


			  