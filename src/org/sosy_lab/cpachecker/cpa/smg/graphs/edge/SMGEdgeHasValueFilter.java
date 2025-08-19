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

public class SMGEdgeHasValueFilter {

  protected SMGObject object;

  public SMGEdgeHasValueFilter() {
    object = null;
  }

  public SMGEdgeHasValueFilter(SMGEdgeHasValueFilter pFilter) {
    object = pFilter.object;
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

  public boolean holdsFor(SMGEdgeHasValue pEdge) {
    if (object != null && object != pEdge.getObject()) {
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
    return String.format("Filter <object=%s>", object);
  }
}
