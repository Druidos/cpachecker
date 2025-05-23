// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.Immutable;
import java.util.Map;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.generic.SMGObjectTemplate;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.optional.SMGOptionalObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;

@Immutable
public sealed class SMGRegion extends SMGObject implements SMGObjectTemplate
    permits SMGAllocation, SMGFunctionObject, SMGReturnObject, SMGStringLiteral, SMGVariable {

  public static SMGRegion materialize(SMGOptionalObject pObject) {
    return new SMGRegion(
        pObject.getSize(),
        pObject.getLabel(),
        pObject.getLevel(),
        SMGObjectKind.REG,
        pObject.getColor());
  }

  public static SMGRegion materialize(SMGAbstractList<?> pObject) {
    return new SMGRegion(
        pObject.getSize(), pObject.getLabel(), 0, SMGObjectKind.REG, pObject.getColor());
  }

  @VisibleForTesting
  public static SMGRegion forTesting(long pSize, String pLabel, int pLevel) {
    return new SMGRegion(pSize, pLabel, pLevel, SMGObjectKind.REG, AllocationColor.COLORLESS);
  }

  // only used in tests
  @VisibleForTesting
  public SMGRegion(long pSize, String pLabel) {
    this(pSize, pLabel, 0, SMGObjectKind.REG, AllocationColor.COLORLESS);
  }

  protected SMGRegion(
      long pSize,
      String pLabel,
      int pLevel,
      SMGObjectKind pKind,
      AllocationColor pAllocationColor) {
    super(pSize, pLabel, pLevel, pKind, pAllocationColor);
  }

  @Override
  public String toString() {
    return "REG" + super.toString();
  }

  @Override
  public boolean isAbstract() {
    return false;
  }

  @Override
  public <T> T accept(SMGObjectVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public SMGObject join(SMGObject pOther, int pDestLevel) {
    assert getColor() == pOther.getColor() : "cannot join " + this + " and " + pOther;
    if (pOther.isAbstract()) {
      // I am concrete, and the other is abstract: the abstraction should
      // know how to join with me
      return pOther.join(this, pDestLevel);
    } else if (getSize() == pOther.getSize()) {
      return new SMGRegion(getSize(), getLabel(), pDestLevel, getKind(), getColor());
    }

    throw new UnsupportedOperationException("join() called on incompatible SMGObjects");
  }

  @Override
  public SMGRegion createConcreteObject(Map<SMGValue, SMGValue> pAbstractToConcretePointerMap) {
    return new SMGRegion(getSize(), getLabel(), 0, SMGObjectKind.REG, AllocationColor.COLORLESS);
  }

  @Override
  public SMGObject copy(int pNewLevel) {
    return new SMGRegion(getSize(), getLabel(), pNewLevel, getKind(), getColor());
  }

  @Override
  public boolean isMoreGeneral(SMGObject pOther) {
    /*There exists no object that is less general than a region.*/
    return false;
  }
}
