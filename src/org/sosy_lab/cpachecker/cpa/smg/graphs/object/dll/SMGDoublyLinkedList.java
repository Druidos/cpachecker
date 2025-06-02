// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object.dll;

import com.google.common.annotations.VisibleForTesting;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.AllocationColor;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGAbstractList;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectKind;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectVisitor;

public class SMGDoublyLinkedList extends SMGAbstractList<SMGDoublyLinkedListShape> {

  @VisibleForTesting
  public static SMGDoublyLinkedList forTesting(
      long pSize, long pHfo, long pNfo, long pPfo, int pMinLength, int level) {
    return new SMGDoublyLinkedList(
        pSize,
        "dll",
        level,
        AllocationColor.COLORLESS,
        new SMGDoublyLinkedListShape(pHfo, pPfo, pNfo),
        pMinLength);
  }

  public SMGDoublyLinkedList(
      SMGObject pObject, int pLevel, SMGDoublyLinkedListShape pShape, int pMinLength) {
    this(pObject.getSize(), pObject.getLabel(), pLevel, pObject.getColor(), pShape, pMinLength);
  }

  private SMGDoublyLinkedList(
      long pBitsize,
      String pLabel,
      int pLevel,
      AllocationColor pColor,
      SMGDoublyLinkedListShape pShape,
      int pMinLength) {
    super(pBitsize, pLabel, pLevel, SMGObjectKind.DLL, pColor, pShape, pMinLength);
  }

  @Override
  public <T> T accept(SMGObjectVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public long getHfo() {
    return getShape().getHfo();
  }

  public long getNfo() {
    return getShape().getNfo();
  }

  public long getPfo() {
    return getShape().getPfo();
  }

  @Override
  public String toString() {
    return "DLL"
        + super.toString()
        + "(hfo="
        + getHfo()
        + ", nfo="
        + getNfo()
        + ", pfo="
        + getPfo()
        + ", len="
        + getMinimumLength()
        + ")";
  }

  @Override
  public SMGObject copy(int level) {
    return copy(getMinimumLength(), level);
  }

  @Override
  public SMGDoublyLinkedList copy(int newLength, int newLevel) {
    return new SMGDoublyLinkedList(this, newLevel, getShape(), newLength);
  }
}
