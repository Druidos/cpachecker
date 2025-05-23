// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2025 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object;

public sealed class SMGVariable extends SMGRegion permits SMGFunctionParameter {
  public SMGVariable(long pBitsize, String pLabel) {
    super(pBitsize, pLabel, 0, SMGObjectKind.REG, AllocationColor.COLORLESS);
  }
}
