// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.edge;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGHasValueEdges;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;

public class SMGEdgeHasValueFilter {

  public static SMGEdgeHasValueFilter objectFilter(SMGObject pObject) {
    return new SMGEdgeHasValueFilter().filterByObject(pObject);
  }

  private SMGObject object = null;
  private SMGValue value = null;
  private boolean valueComplement = false;
  private Long offset = null;
  private long sizeInBits = -1;

  public SMGObject getObject() {
    return object;
  }

  public SMGValue getValue() {
    return value;
  }

  public Long getOffset() {
    return offset;
  }

  public long getSize() {
    return sizeInBits;
  }

  @VisibleForTesting
  public SMGEdgeHasValueFilter filterByObject(SMGObject pObject) {
    object = pObject;
    return this;
  }

  public SMGEdgeHasValueFilter filterHavingValue(SMGValue pValue) {
    value = pValue;
    valueComplement = false;
    return this;
  }

  public SMGEdgeHasValueFilter filterNotHavingValue(SMGValue pValue) {
    value = pValue;
    valueComplement = true;
    return this;
  }

  public SMGEdgeHasValueFilter filterAtOffset(long pOffset) {
    offset = pOffset;
    return this;
  }

  public SMGEdgeHasValueFilter filterBySize(long pSizeInBits) {
    Preconditions.checkArgument(pSizeInBits >= 0, "negative sizes not allowed for filtering");
    sizeInBits = pSizeInBits;
    return this;
  }

  public boolean holdsFor(SMGEdgeHasValue pEdge) {
    if (object != null && object != pEdge.getObject()) {
      return false;
    }

    if (value != null) {
      if (valueComplement && pEdge.getValue().equals(value)) {
        return false;
      } else if (!valueComplement && !pEdge.getValue().equals(value)) {
        return false;
      }
    }

    if (offset != null && offset != pEdge.getOffset()) {
      return false;
    }

    if (sizeInBits >= 0 && sizeInBits != pEdge.getSizeInBits()) {
      return false;
    }

    return true;
  }

  public SMGHasValueEdges filter(SMGHasValueEdges pEdges) {
    SMGHasValueEdges filtered;
    if (object != null) {
      filtered = pEdges.getEdgesForObject(object);
    } else {
      filtered = pEdges.getHvEdges();
    }
    return filtered.filter(this);
  }

  public static SMGEdgeHasValueFilter valueFilter(SMGValue pValue) {
    return new SMGEdgeHasValueFilter().filterHavingValue(pValue);
  }

  @Override
  public String toString() {
    return String.format(
        "Filter %s<object=%s@%d, value=%s, size=%d>",
        valueComplement ? "" : "NOT", object, offset, value, sizeInBits);
  }
}