// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2025 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object;

import org.sosy_lab.cpachecker.cpa.smg.CLangStackFrame;

public final class SMGReturnObject extends SMGRegion {
  public SMGReturnObject(long pBitsize) {
    // TODO move retval label here and make it private
    super(pBitsize, CLangStackFrame.RETVAL_LABEL, 0, SMGObjectKind.REG, AllocationColor.COLORLESS);
  }
}
