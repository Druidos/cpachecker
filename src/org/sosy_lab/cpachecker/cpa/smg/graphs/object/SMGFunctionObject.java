// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2025 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object;

public final class SMGFunctionObject extends SMGRegion {
  public SMGFunctionObject(String pLabel) {
    super(0, pLabel, 0, SMGObjectKind.REG, AllocationColor.COLORLESS);
  }
}
