// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.edge;

import com.google.common.annotations.VisibleForTesting;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGHasValueEdges;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;

public class SMGEdgeHasValueFilter {

  protected SMGObject object;
  private SMGValue value;
  private boolean valueComplement;

  public SMGEdgeHasValueFilter() {
    object = null;
    value = null;
    valueComplement = false;
  }

  public SMGEdgeHasValueFilter(SMGEdgeHasValueFilter pFilter) {
    object = pFilter.object;
    value = pFilter.value;
    valueComplement = pFilter.valueComplement;
  }

  public SMGObject getObject() {
    return object;
  }

  public SMGValue getValue() {
    return value;
  }

  @VisibleForTesting
  public SMGEdgeHasValueFilter filterByObject(SMGObject pObject) {
    SMGEdgeHasValueFilter filter = new SMGEdgeHasValueFilter(this);
    filter.object = pObject;
    return filter;
  }

  public SMGEdgeHasValueFilter filterHavingValue(SMGValue pValue) {
    SMGEdgeHasValueFilter filter = new SMGEdgeHasValueFilter(this);
    filter.value = pValue;
    filter.valueComplement = false;
    return filter;
  }

  public SMGEdgeHasValueFilter filterNotHavingValue(SMGValue pValue) {
    SMGEdgeHasValueFilter filter = new SMGEdgeHasValueFilter(this);
    filter.value = pValue;
    filter.valueComplement = true;
    return filter;
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

    return true;
  }

  public Iterable<SMGEdgeHasValue> filter(SMGHasValueEdges pEdges) {
    if (object != null) {
      pEdges = pEdges.getEdgesForObject(object);
    }
    return pEdges.filter(this);
  }

  public static SMGEdgeHasValueFilter valueFilter(SMGValue pValue) {
    return new SMGEdgeHasValueFilter().filterHavingValue(pValue);
  }

  @Override
  public String toString() {
    return String.format(
        "Filter %s<object=%s, value=%s>", valueComplement ? "NOT" : "", object, value);
  }
}
