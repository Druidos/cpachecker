// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.join;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.cpachecker.cpa.smg.SMGInconsistentException;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGHasValueEdgeSet;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGHasValueEdges;
import org.sosy_lab.cpachecker.cpa.smg.graphs.UnmodifiableSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;

class SMGJoinFields {
  private final SMG newSMG1;
  private final SMG newSMG2;
  private SMGJoinStatus status = SMGJoinStatus.EQUAL;

  /** Algorithm 3 from FIT-TR-2012-04 */
  public SMGJoinFields(
      final UnmodifiableSMG pSMG1, final UnmodifiableSMG pSMG2, SMGObject pObj1, SMGObject pObj2) {

    Preconditions.checkArgument(
        pObj1.getSize() == pObj2.getSize(),
        "SMGJoinFields object arguments need to have identical size");
    Preconditions.checkArgument(
        pSMG1.getObjects().contains(pObj1) && pSMG2.getObjects().contains(pObj2),
        "SMGJoinFields object arguments need to be included in parameter SMGs");

    newSMG1 = pSMG1.copyOf();
    newSMG2 = pSMG2.copyOf();

    // Algorithm 3 from FIT-TR-2012-04, line 2
    setCompatibleHVEdgesToSMG(newSMG1, pSMG2, pObj1, pObj2);
    setCompatibleHVEdgesToSMG(newSMG2, pSMG1, pObj2, pObj1);

    // Algorithm 3 from FIT-TR-2012-04, line 4
    joinFieldsRelaxStatus(pSMG1, newSMG1, SMGJoinStatus.LEFT_ENTAIL, pObj1);
    joinFieldsRelaxStatus(pSMG2, newSMG2, SMGJoinStatus.RIGHT_ENTAIL, pObj2);

    SMGHasValueEdges smg2Extension = mergeNonNullHasValueEdges(pSMG1, newSMG2, pObj1, pObj2);
    SMGHasValueEdges smg1Extension = mergeNonNullHasValueEdges(pSMG2, newSMG1, pObj2, pObj1);

    // Algorithm 3 from FIT-TR-2012-04, line 5
    for (SMGEdgeHasValue edge : smg1Extension) {
      newSMG1.addValue(edge.getValue());
      newSMG1.addHasValueEdge(edge);
    }
    for (SMGEdgeHasValue edge : smg2Extension) {
      newSMG2.addValue(edge.getValue());
      newSMG2.addHasValueEdge(edge);
    }
  }

  public SMGJoinStatus getStatus() {
    return status;
  }

  public UnmodifiableSMG getSMG1() {
    return newSMG1;
  }

  public UnmodifiableSMG getSMG2() {
    return newSMG2;
  }

  @VisibleForTesting
  public static SMGHasValueEdges mergeNonNullHasValueEdges(
      UnmodifiableSMG pSMG1, UnmodifiableSMG pSMG2, SMGObject pObj1, SMGObject pObj2) {
    SMGHasValueEdges returnSet = new SMGHasValueEdgeSet();

    if (pSMG1.getHVEdgesFrom(pObj1).equals(pSMG2.getHVEdgesFrom(pObj2))) {
      return returnSet;
    }

    SMGEdgeHasValueFilter filterForSMG1 =
        new SMGEdgeHasValueFilter()
            .filterByObject(pObj1)
            .filterNotHavingValue(SMGZeroValue.INSTANCE);

    for (SMGEdgeHasValue edge : pSMG1.getHVEdges(filterForSMG1)) {
      if (pSMG2
          .getHVEdges()
          .getOverlappingEdges(pObj2, edge.getOffset(), edge.getSizeInBits())
          .isEmpty()) {
        returnSet =
            returnSet.addEdgeAndCopy(
                new SMGEdgeHasValue(
                    edge.getSizeInBits(), edge.getOffset(), pObj2, SMGKnownSymValue.of()));
      }
    }

    return returnSet;
  }

  // Checks whether the nullified blocks are same in the new SMG as in the original SMG
  // or they got shrank / removed
  @VisibleForTesting
  void joinFieldsRelaxStatus(
      UnmodifiableSMG pOrigSMG,
      UnmodifiableSMG pNewSMG,
      SMGJoinStatus pNewStatus,
      SMGObject pObject) {
    // important: the new null edge block can only by same size or smaller!

    // for every zero-value bit that is not covered by some zero-value edge in the new SMG,
    // update the join status
    for (SMGEdgeHasValue origEdge :
        pOrigSMG.getHVEdgesFrom(pObject).getEdgesTo(SMGZeroValue.INSTANCE)) {
      Optional<SMGEdgeHasValue> newCoveringEdge =
          pNewSMG
              .getHVEdges()
              .getCoveringEdge(pObject, origEdge.getOffset(), origEdge.getSizeInBits());
      if (newCoveringEdge.isEmpty() || !newCoveringEdge.orElseThrow().getValue().isZero()) {
        // there is some old zero-value bit that is not covered by zero-value in the new SMG
        status = status.updateWith(pNewStatus);
        return; // further updates do nothing
      }
      // TODO idk why, but sure, check it is not bigger
      SMGEdgeHasValue newEdge = newCoveringEdge.orElseThrow();
      assert origEdge.getOffset() == newEdge.getOffset() : "null block got bigger on its start";
      assert origEdge.getEndOffset() == newEdge.getEndOffset() : "null block got bigger on its end";
    }
  }

  /**
   * split nullified fields such that each (partial) field corresponds to a field in the other SMG.
   */
  @VisibleForTesting
  static void setCompatibleHVEdgesToSMG(
      SMG pSMG, UnmodifiableSMG pSMG2, SMGObject pObj1, SMGObject pObj2) {

    if (pSMG.getHVEdgesFrom(pObj1).equals(pSMG2.getHVEdgesFrom(pObj2))) {
      return;
    }

    Iterable<SMGEdgeHasValue> edgesToRemove =
        pSMG.getHVEdgesFrom(pObj1).getEdgesTo(SMGZeroValue.INSTANCE);
    Set<SMGEdgeHasValue> edgesToAdd1 = getHVSetOfCommonNullValues(pSMG, pSMG2, pObj1, pObj2);
    Set<SMGEdgeHasValue> edgesToAdd2 = getHVSetOfMissingNullValues(pSMG, pSMG2, pObj1, pObj2);

    // step 2a
    for (SMGEdgeHasValue edge : edgesToRemove) {
      pSMG.removeHasValueEdge(edge);
    }
    // step 2b
    for (SMGEdgeHasValue edge : edgesToAdd1) {
      pSMG.addHasValueEdge(edge);
    }
    // step 2c
    for (SMGEdgeHasValue edge : edgesToAdd2) {
      pSMG.addHasValueEdge(edge);
    }
  }

  /**
   * get all HV-Edges that are part of both SMGs, such that in one SMG the edge points to NULL and
   * in the other one the edge points to a non-NULL value.
   */
  @VisibleForTesting
  static Set<SMGEdgeHasValue> getHVSetOfMissingNullValues(
      UnmodifiableSMG pSMG1, UnmodifiableSMG pSMG2, SMGObject pObj1, SMGObject pObj2) {
    Set<SMGEdgeHasValue> retset = new LinkedHashSet<>();

    SMGEdgeHasValueFilter nonNullPtrInSmg2 =
        new SMGEdgeHasValueFilter()
            .filterByObject(pObj2)
            .filterNotHavingValue(SMGZeroValue.INSTANCE);

    for (SMGEdgeHasValue edge : pSMG2.getHVEdges(nonNullPtrInSmg2)) {
      Optional<SMGEdgeHasValue> coveringEdge =
          pSMG1.getHVEdges().getCoveringEdge(pObj1, edge.getOffset(), edge.getSizeInBits());
      if (coveringEdge.isPresent() && coveringEdge.orElseThrow().getValue().isZero()) {
        retset.add(
            new SMGEdgeHasValue(
                edge.getSizeInBits(), edge.getOffset(), pObj1, SMGZeroValue.INSTANCE));
      }
    }
    return retset;
  }

  /** get all HV-Edges that are common in both SMGs, i.e. where both objects point to NULL. */
  @VisibleForTesting
  static ImmutableSet<SMGEdgeHasValue> getHVSetOfCommonNullValues(
      UnmodifiableSMG pSMG1, UnmodifiableSMG pSMG2, SMGObject pObj1, SMGObject pObj2) {
    ImmutableSet.Builder<SMGEdgeHasValue> resultBuilder = ImmutableSet.builder();

    for (SMGEdgeHasValue edge1 : pSMG1.getHVEdgesFrom(pObj1).getEdgesTo(SMGZeroValue.INSTANCE)) {
      for (SMGEdgeHasValue edge2 :
          pSMG2.getHVEdges().getOverlappingEdges(pObj2, edge1.getOffset(), edge1.getSizeInBits())) {
        if (edge2.getValue().isZero()) {
          long ofs = Long.max(edge1.getOffset(), edge2.getOffset());
          long end = Long.min(edge1.getEndOffset(), edge2.getEndOffset());
          long bitsize = end - ofs;
          resultBuilder.add(new SMGEdgeHasValue(bitsize, ofs, pObj1, SMGZeroValue.INSTANCE));
        }
      }
    }

    return resultBuilder.build();
  }

  private static void checkResultConsistencySingleSide(
      UnmodifiableSMG pSMG1, SMGObject pObj1, UnmodifiableSMG pSMG2, SMGObject pObj2)
      throws SMGInconsistentException {

    for (SMGEdgeHasValue edge1 : pSMG1.getHVEdgesFrom(pObj1).getEdgesTo(SMGZeroValue.INSTANCE)) {
      Optional<SMGEdgeHasValue> edge2 =
          pSMG2.getHVEdges().getEdge(pObj2, edge1.getOffset(), edge1.getSizeInBits());
      if (edge2.isEmpty()) {
        throw new SMGInconsistentException(
            "SMGJoinFields output assertions do not hold: no has-value edge matches zero-value "
                + edge1);
      }
      // edge2 either matches edge1's bits
      // or it is part of a zero-value edge that covers those
      // which means there is no zero-value edge that covers starting bits partially
      if (!pSMG2.isPointer(edge2.orElseThrow().getValue())) {
        throw new SMGInconsistentException(
            "SMGJoinFields output assertions do not hold: value of "
                + edge2
                + " that matches "
                + edge1
                + " is not a pointer");
      }
    }
  }

  public static void checkResultConsistency(
      UnmodifiableSMG pSMG1, UnmodifiableSMG pSMG2, SMGObject pObj1, SMGObject pObj2)
      throws SMGInconsistentException {

    if (Iterables.size(
            pSMG1.getHVEdges(
                new SMGEdgeHasValueFilter()
                    .filterByObject(pObj1)
                    .filterNotHavingValue(SMGZeroValue.INSTANCE)))
        != Iterables.size(
            pSMG2.getHVEdges(
                new SMGEdgeHasValueFilter()
                    .filterByObject(pObj2)
                    .filterNotHavingValue(SMGZeroValue.INSTANCE)))) {
      throw new SMGInconsistentException(
          "SMGJoinFields output assertion does not hold: "
              + "the objects do not have the same count of non-zero fields");
    }

    checkResultConsistencySingleSide(pSMG1, pObj1, pSMG2, pObj2);
    checkResultConsistencySingleSide(pSMG2, pObj2, pSMG1, pObj1);
  }
}
