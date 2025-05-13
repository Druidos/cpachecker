// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object;

import org.sosy_lab.common.UniqueIdGenerator;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.dll.SMGDoublyLinkedList;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;

/**
 * SMGs consists of two types of nodes: {@link SMGObject}s and {@link SMGValue}s. {@link SMGObject}s
 * are further derived into {@link SMGRegion}s and abstractions like {@link SMGDoublyLinkedList}.
 * There is a special {@link SMGNullObject} representing the target of NULL.
 */
public abstract class SMGObject implements Comparable<SMGObject> {

  private final long size;
  private final String label;
  private final int level;
  private final SMGObjectKind kind;
  private final int id;
  private final AllocationColor color;
  private static final UniqueIdGenerator uniqueIdGenerator = new UniqueIdGenerator();

  public SMGObjectKind getKind() {
    return kind;
  }

  protected SMGObject(
      long pSize, String pLabel, int pLevel, SMGObjectKind pKind, AllocationColor pColor) {
    size = pSize;
    label = pLabel;
    level = pLevel;
    kind = pKind;
    id = uniqueIdGenerator.getFreshId();
    color = pColor;
  }

  public abstract SMGObject copy(int pNewLevel);

  public String getLabel() {
    return label;
  }

  public long getSize() {
    return size;
  }

  public AllocationColor getColor() {
    return color;
  }

  public abstract boolean isAbstract();

  public abstract <T> T accept(SMGObjectVisitor<T> visitor);

  /**
   * Compares objects and determines, if this object is more general than given object. If this
   * object is more general than the given object, then a smg resulting in replacing the given
   * object with this object would cover strictly more states.
   *
   * @param pOther other object to be compared with this object.
   * @return Returns true iff this object is more general than given object. False otherwise.
   */
  public abstract boolean isMoreGeneral(SMGObject pOther);

  /**
   * Compute the result of a join.
   *
   * @param pOther object to join with
   * @param pDestLevel increase Nesting level.
   */
  public abstract SMGObject join(SMGObject pOther, int pDestLevel);

  public int getLevel() {
    return level;
  }

  public int getId() {
    return id;
  }

  @Override
  public int compareTo(SMGObject o) {
    return Integer.compare(getId(), o.getId());
  }
}
