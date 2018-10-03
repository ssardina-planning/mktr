#*******************************************************************************
# MKTR - Minimal k-Treewidth Relaxation
#
# Copyright (C) 2018 
# Max Waters (max.waters@rmit.edu.au)
# RMIT University, Melbourne VIC 3000
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#*******************************************************************************
CLASSPATH=lib/pplib-0.1.jar:lib/args4j-2.33.jar:lib/libtw.jar:lib/pddl4j-3.5.0.jar
MAIN=au.rmit.agtgrp.pp.main.MktrMain

java -cp $CLASSPATH $MAIN "$@"
