// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object.optional;

import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGAbstractObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectKind;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectVisitor;

/**
 * Abstraction that represents states either containing a concrete region represented by this
 * abstract object, or no region. Every field of this object may only contain the same value. Every
 * pointer leading to this object either lead to the concrete region represented by this object, or
 * to the one value leading from this object.
 */
public class SMGOptionalObject extends SMGAbstractObject {

  public SMGOptionalObject(SMGObject pObject, int pLevel) {
    super(
        pObject.getSize(), pObject.getLabel(), pLevel, SMGObjectKind.OPTIONAL, pObject.getColor());
  }

  @Override
  public boolean matchGenericShape(SMGAbstractObject pOther) {
    return pOther.getKind() == SMGObjectKind.OPTIONAL;
  }

  @Override
  public boolean matchSpecificShape(SMGAbstractObject pOther) {
    return matchGenericShape(pOther) && getSize() == pOther.getSize();
  }

  @Override
  public SMGObject copy(int pNewLevel) {
    return new SMGOptionalObject(this, pNewLevel);
  }

  @Override
  public boolean isMoreGeneral(SMGObject pOther) {

    return switch (pOther.getKind()) {
      case REG -> getSize() == pOther.getSize();
      default -> false;
    };
  }

  @Override
  public <T> T accept(SMGObjectVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public SMGObject join(SMGObject pOther, int pDestLevel) {

    /*Only join if other is region or optional object, otherwise
     * let other object join.*/
    assert getColor() == pOther.getColor() : "cannot join " + this + " and " + pOther;
    assert getSize() == pOther.getSize() : "cannot join " + this + " and " + pOther;

    int level = Math.max(getLevel(), pOther.getLevel());

    return switch (pOther.getKind()) {
      case REG, OPTIONAL -> copy(level);
      default -> pOther.join(this, pDestLevel);
    };
  }

  @Override
  public String toString() {
    return "OPTIONAL(id="
        + getId()
        + " "
        + getLabel()
        + " size="
        + getSize()
        + ", level="
        + getLevel()
        + ")";
  }
}
