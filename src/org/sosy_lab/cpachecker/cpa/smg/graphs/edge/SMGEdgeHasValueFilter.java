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

  public SMGEdgeHasValueFilter() {
    object = null;
    value = null;
  }

  public SMGEdgeHasValueFilter(SMGEdgeHasValueFilter pFilter) {
    object = pFilter.object;
    value = pFilter.value;
  }

  public SMGObject getObject() {
    return object;
  }

  @VisibleForTesting
  public SMGEdgeHasValueFilter filterByObject(SMGObject pObject) {
    SMGEdgeHasValueFilter filter = new SMGEdgeHasValueFilter(this);
    filter.object = pObject;
    return filter;
  }

  public SMGEdgeHasValueFilter filterNotHavingValue(SMGValue pValue) {
    SMGEdgeHasValueFilter filter = new SMGEdgeHasValueFilter(this);
    filter.value = pValue;
    return filter;
  }

  public boolean holdsFor(SMGEdgeHasValue pEdge) {
    if (object != null && object != pEdge.getObject()) {
      return false;
    }

    if (pEdge.getValue().equals(value)) {
      return false;
    }

    return true;
  }

  public Iterable<SMGEdgeHasValue> filter(SMGHasValueEdges pEdges) {
    if (object != null) {
      pEdges = pEdges.getEdgesForObject(object);
    }
    return pEdges.filter(this);
  }

  @Override
  public String toString() {
    return String.format("Filter <object=%s, NOT value=%s>", object, value);
  }
}
