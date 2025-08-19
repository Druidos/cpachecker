// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs;

import java.util.Collection;
import java.util.Optional;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;

/**
 * An immutable collection of has-value-edges. Should keep invariant: All has-value-edges,
 * corresponding to one object, shouldn't interleave. Also it is possible to provide implementation
 * with sorting by objects and offsets
 */
public interface SMGHasValueEdges extends Iterable<SMGEdgeHasValue> {

  // Modifying methods

  SMGHasValueEdges removeAllEdgesOfObjectAndCopy(SMGObject pObj);

  SMGHasValueEdges addEdgeAndCopy(SMGEdgeHasValue pEdge);

  SMGHasValueEdges removeEdgeAndCopy(SMGEdgeHasValue pEdge);

  // Querying methods

  /** get all outgoing edges of all {@link SMGObject}s. */
  SMGHasValueEdges getHvEdges();

  Iterable<SMGEdgeHasValue> filter(SMGEdgeHasValueFilter pFilter);

  /** get all outgoing edges of an {@link SMGObject}, e.g., all values of this object. */
  SMGHasValueEdges getEdgesForObject(SMGObject pObject);

  /** get the edge that fully covers the range (if present) */
  Optional<SMGEdgeHasValue> getCoveringEdge(SMGObject pObject, long pOffset, long pBitsize);

  /** Get all edges that overlap with the range. The collection returned is unmodifiable. */
  Collection<SMGEdgeHasValue> getOverlappingEdges(SMGObject pObject, long pOffset, long pBitsize);

  /**
   * Get the edge matching the (object, offset, bitsize).
   *
   * <p>If an edge from the object starts from the offset and has the bitsize, return it.
   *
   * <p>If a zero-value edge covers the queried bits, return the matching subedge.
   *
   * <p>Otherwise, return Optional.empty().
   */
  Optional<SMGEdgeHasValue> getEdge(SMGObject pObject, long pOffset, long pBitsize);

  /**
   * Get the edge starting from pObject:pOffset.
   *
   * <p>If a zero-value edge covers the requested bit, return the sub-edge starting from pOffset.
   */
  Optional<SMGEdgeHasValue> getEdge(SMGObject pObject, long pOffset);

  /** Get all edges that have (lead to) pValue. */
  Iterable<SMGEdgeHasValue> getEdgesTo(SMGValue pValue);

  /** get all edges that have (lead to) a value other than zero */
  Iterable<SMGEdgeHasValue> getNonZeroEdges();

  int size();

  boolean contains(SMGEdgeHasValue pHv);

  SMGHasValueEdges addEdgesForObject(Iterable<SMGEdgeHasValue> pEdgesSet);

  boolean isEmpty();

  Iterable<SMGEdgeHasValue> remove(SMGHasValueEdges pEdgesToRemove);
}
