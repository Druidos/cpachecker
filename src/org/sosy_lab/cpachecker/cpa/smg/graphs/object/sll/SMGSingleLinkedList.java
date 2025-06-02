// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object.sll;

import com.google.common.annotations.VisibleForTesting;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.AllocationColor;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGAbstractList;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectKind;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectVisitor;

public final class SMGSingleLinkedList extends SMGAbstractList<SMGSingleLinkedListShape> {

  @VisibleForTesting
  public static SMGSingleLinkedList forTesting(
      long pSize, long pHfo, long pNfo, int pMinLength, int level) {
    return new SMGSingleLinkedList(
        pSize,
        "sll",
        level,
        AllocationColor.COLORLESS,
        new SMGSingleLinkedListShape(pHfo, pNfo),
        pMinLength);
  }

  public SMGSingleLinkedList(
      SMGObject pObject, int pLevel, SMGSingleLinkedListShape pShape, int pMinLength) {
    this(pObject.getSize(), pObject.getLabel(), pLevel, pObject.getColor(), pShape, pMinLength);
  }

  private SMGSingleLinkedList(
      long pBitsize,
      String pLabel,
      int pLevel,
      AllocationColor pColor,
      SMGSingleLinkedListShape pShape,
      int pMinLength) {
    super(pBitsize, pLabel, pLevel, SMGObjectKind.SLL, pColor, pShape, pMinLength);
  }

  public long getNfo() {
    return getShape().getNfo();
  }

  public long getHfo() {
    return getShape().getHfo();
  }

  @Override
  public <T> T accept(SMGObjectVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public String toString() {
    return "SLL"
        + super.toString()
        + "(hfo="
        + getHfo()
        + ", nfo="
        + getNfo()
        + ", len="
        + getMinimumLength()
        + ")";
  }

  @Override
  public SMGObject copy(int pNewLevel) {
    return copy(getMinimumLength(), pNewLevel);
  }

  @Override
  public SMGSingleLinkedList copy(int newLength, int pNewLevel) {
    return new SMGSingleLinkedList(this, pNewLevel, getShape(), newLength);
  }
}
