/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.differential;

import org.sosy_lab.cpachecker.core.interfaces.conditions.AvoidanceReportingState;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;

public enum DifferentialState implements AvoidanceReportingState {
  MODIFIED(true),
  MODIFIED_REACHABLE(false),
  MODIFIED_NOT_REACHABLE(false);

  private final boolean isModified;

  DifferentialState(boolean pIsModified) {
    isModified = pIsModified;
  }

  @Override
  public boolean mustDumpAssumptionForAvoidance() {
    return isModified;
  }

  @Override
  public BooleanFormula getReasonFormula(FormulaManagerView mgr) {
    return mgr.getBooleanFormulaManager().makeVariable("EQUAL_TO_BASE_PROGRAM");
  }
}