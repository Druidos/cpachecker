// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object;

public abstract class SMGAbstractObject extends SMGObject {

  protected SMGAbstractObject(
      long pSize, String pLabel, int pLevel, SMGObjectKind pKind, AllocationColor pColor) {
    super(pSize, pLabel, pLevel, pKind, pColor);
  }

  @Override
  public final boolean isAbstract() {
    return true;
  }

  public abstract boolean matchGenericShape(SMGAbstractObject pOther);

  public abstract boolean matchSpecificShape(SMGAbstractObject pOther);
}
