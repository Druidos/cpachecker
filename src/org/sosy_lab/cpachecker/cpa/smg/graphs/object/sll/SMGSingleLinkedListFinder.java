// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object.sll;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.sosy_lab.cpachecker.cpa.smg.SMGAbstractionBlock;
import org.sosy_lab.cpachecker.cpa.smg.SMGAbstractionCandidate;
import org.sosy_lab.cpachecker.cpa.smg.SMGAbstractionFinder;
import org.sosy_lab.cpachecker.cpa.smg.SMGInconsistentException;
import org.sosy_lab.cpachecker.cpa.smg.SMGTargetSpecifier;
import org.sosy_lab.cpachecker.cpa.smg.SMGUtils;
import org.sosy_lab.cpachecker.cpa.smg.UnmodifiableSMGState;
import org.sosy_lab.cpachecker.cpa.smg.graphs.UnmodifiableCLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgePointsTo;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectKind;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.smg.join.SMGJoinSubSMGsForAbstraction;

public class SMGSingleLinkedListFinder extends SMGAbstractionFinder {

  @VisibleForTesting
  public SMGSingleLinkedListFinder() {}

  public SMGSingleLinkedListFinder(
      int pSeqLengthEqualityThreshold,
      int pSeqLengthEntailmentThreshold,
      int pSeqLengthIncomparableThreshold) {
    super(
        pSeqLengthEqualityThreshold,
        pSeqLengthEntailmentThreshold,
        pSeqLengthIncomparableThreshold);
  }

  @Override
  public Set<SMGAbstractionCandidate> traverse(
      UnmodifiableCLangSMG pSmg,
      UnmodifiableSMGState pSMGState,
      Set<SMGAbstractionBlock> pAbstractionBlocks)
      throws SMGInconsistentException {
    SMGJoinSllProgress pProgress = new SMGJoinSllProgress();

    for (SMGObject object : pSmg.getHeapObjects()) {
      startTraversal(object, pSmg, pSMGState, pProgress);
    }

    Set<SMGSingleLinkedListCandidateSequenceBlock> sllBlocks =
        FluentIterable.from(pAbstractionBlocks)
            .filter(SMGSingleLinkedListCandidateSequenceBlock.class)
            .toSet();

    return pProgress.getValidCandidates(
        seqLengthEqualityThreshold,
        seqLengthEntailmentThreshold,
        seqLengthIncomparableThreshold,
        pSmg,
        sllBlocks);
  }

  private void startTraversal(
      SMGObject pObject,
      UnmodifiableCLangSMG pSmg,
      UnmodifiableSMGState pSmgState,
      SMGJoinSllProgress pProgress)
      throws SMGInconsistentException {
    if (pProgress.containsCandidateMap(pObject)) {
      // Processed already in continueTraversal
      return;
    }

    createCandidatesOfObject(pObject, pSmg, pSmgState, pProgress);
  }

  private void createCandidatesOfObject(
      SMGObject pObject,
      UnmodifiableCLangSMG pSmg,
      UnmodifiableSMGState pSMGState,
      SMGJoinSllProgress pProgress)
      throws SMGInconsistentException {

    if (!pSmg.isObjectValid(pObject)) {
      return;
    }

    if (!(pObject.getKind() == SMGObjectKind.SLL
        || pObject.getKind() == SMGObjectKind.REG
        || pObject.getKind() == SMGObjectKind.OPTIONAL)) {
      return;
    }

    boolean startObjectIsSLL = pObject.getKind() == SMGObjectKind.SLL;

    for (SMGEdgeHasValue hveNext : pSmg.getHVEdgesFrom(pObject)) {

      long nfo = hveNext.getOffset();
      SMGValue nextPointer = hveNext.getValue();

      if (!pSmg.isPointer(nextPointer)) {
        continue;
      }

      SMGEdgePointsTo nextPointerEdge = pSmg.getPointer(nextPointer);
      long hfo = nextPointerEdge.getOffset();

      if (startObjectIsSLL) {
        SMGSingleLinkedList sll = ((SMGSingleLinkedList) pObject);
        if (hfo != sll.getHfo() || nfo != sll.getNfo()) {
          continue;
        }
      }

      SMGTargetSpecifier nextPointerTg = nextPointerEdge.getTargetSpecifier();

      if (!(nextPointerTg == SMGTargetSpecifier.REGION
          || nextPointerTg == SMGTargetSpecifier.FIRST
          || nextPointerTg == SMGTargetSpecifier.OPT)) {
        continue;
      }

      SMGObject nextObject = nextPointerEdge.getObject();

      if (pObject == nextObject) {
        continue;
      }

      if (pObject.getSize() != nextObject.getSize()) {
        continue;
      }

      if (nextObject.getKind() == SMGObjectKind.SLL) {
        SMGSingleLinkedList sll = ((SMGSingleLinkedList) nextObject);
        if (hfo != sll.getHfo() || nfo != sll.getNfo()) {
          continue;
        }
      }

      if (!(nextObject.getKind() == SMGObjectKind.SLL
          || nextObject.getKind() == SMGObjectKind.REG
          || nextObject.getKind() == SMGObjectKind.OPTIONAL)) {
        continue;
      }

      if (nextObject.getColor() != pObject.getColor()) {
        // do not abstract differently colored objects into lists
        return;
      }

      if (!pSmg.isObjectValid(nextObject) || !(nextObject.getLevel() == pObject.getLevel())) {
        continue;
      }

      // Constrains on pointer bitsizes were introduced in fbcbaeb661b4e32d5fba19a232ddbe1aaea3d7fc
      ImmutableSet.Builder<Long> typeSizesBuilder = ImmutableSet.builder();
      for (SMGEdgePointsTo pte : SMGUtils.getPointerToThisObject(pObject, pSmg)) {
        Iterable<SMGEdgeHasValue> hves =
            pSmg.getHVEdges(new SMGEdgeHasValueFilter().filterHavingValue(pte.getValue()));
        Iterable<Long> bitsizes = Iterables.transform(hves, SMGEdgeHasValue::getSizeInBits);

        if (pte.getValue().isZero()) {
          // zero-value edges can be bigger
          bitsizes = Iterables.filter(bitsizes, bitsize -> bitsize >= pSmg.getSizeofPtrInBits());
          typeSizesBuilder.addAll(bitsizes);

        } else {
          // non-zero edges have to be strict
          boolean hasStrictBitsize =
              Iterables.any(bitsizes, bitsize -> bitsize == pSmg.getSizeofPtrInBits());
          if (hasStrictBitsize) {
            typeSizesBuilder.add((long) pSmg.getSizeofPtrInBits());
          }
        }
      }
      final ImmutableSet<Long> typeSizesOfThisObject = typeSizesBuilder.build();

      long nextSize = hveNext.getSizeInBits();

      if (!typeSizesOfThisObject.contains(nextSize)) {
        continue;
      }

      SMGSingleLinkedListCandidate candidate =
          new SMGSingleLinkedListCandidate(pObject, nfo, hfo, nextSize, pSmg.getMachineModel());
      pProgress.initializeCandidiate(candidate);
      continueTraversal(nextPointer, candidate, pSmg, pSMGState, pProgress);
    }
  }

  private void continueTraversal(
      SMGValue pValue,
      SMGSingleLinkedListCandidate pPrevCandidate,
      UnmodifiableCLangSMG pSmg,
      UnmodifiableSMGState pSmgState,
      SMGJoinSllProgress pProgress)
      throws SMGInconsistentException {

    SMGEdgePointsTo pt = pSmg.getPointer(pValue);
    SMGObject nextObject = pt.getObject();
    SMGObject startObject = pPrevCandidate.getStartObject();

    // First, calculate the longest mergeable sequence of the next object
    if (!pSmg.isHeapObject(nextObject)) {
      return;
    }

    if (!pProgress.containsCandidateMap(nextObject)) {
      startTraversal(nextObject, pSmg, pSmgState, pProgress);
    }

    Long nfo = pPrevCandidate.getShape().getNfo();
    Long hfo = pPrevCandidate.getShape().getHfo();

    SMGSingleLinkedListCandidate candidate;

    if (!pProgress.containsCandidate(nextObject, nfo)) {
      /* candidate not doubly linked with next object,
       * last object in sequence.
       */

      if (!pSmg.isObjectValid(nextObject) || !(nextObject.getLevel() == startObject.getLevel())) {
        return;
      }

      if (!(nextObject.getKind() == SMGObjectKind.SLL
          || nextObject.getKind() == SMGObjectKind.REG
          || nextObject.getKind() == SMGObjectKind.OPTIONAL)) {
        return;
      }

      if (nextObject.getColor() != startObject.getColor()) {
        // do not abstract differently colored objects into lists
        return;
      }

      // TODO At the moment, we still demand that a value is found at prev or next.

      Optional<SMGEdgeHasValue> nextObjectNextPointer =
          pSmg.getHVEdgeOfPtrBitsizeAt(nextObject, nfo);

      if (nextObjectNextPointer.isEmpty()) {
        return;
      }

      SMGEdgeHasValue nextEdge = nextObjectNextPointer.orElseThrow();

      if (!pSmg.isPointer(nextEdge.getValue())) {
        return;
      }

      candidate =
          new SMGSingleLinkedListCandidate(
              nextObject, nfo, hfo, nextEdge.getSizeInBits(), pSmg.getMachineModel());
      pProgress.initializeLastInSequenceCandidate(candidate);

    } else {
      candidate = pProgress.getCandidate(nextObject, nfo);
    }

    if (candidate.getShape().getHfo() != pPrevCandidate.getShape().getHfo()) {
      return;
    }

    // Second, find out if the subsmgs are mergeable
    SMGJoinSubSMGsForAbstraction join =
        new SMGJoinSubSMGsForAbstraction(
            pSmg.copyOf(), startObject, nextObject, candidate, pSmgState);

    if (!join.isDefined()) {
      return;
    }

    Set<SMGObject> nonSharedObject1 = join.getNonSharedObjectsFromSMG1();
    Set<SMGValue> nonSharedValues1 = join.getNonSharedValuesFromSMG1();
    Set<SMGObject> nonSharedObject2 = join.getNonSharedObjectsFromSMG2();
    Set<SMGValue> nonSharedValues2 = join.getNonSharedValuesFromSMG2();

    Set<SMGObject> objectsOfSubSmg1 = new HashSet<>();
    Set<SMGObject> objectsOfSubSmg2 = new HashSet<>();
    Set<SMGValue> valuesOfSubSmg1 = new HashSet<>();
    Set<SMGValue> valuesOfSubSmg2 = new HashSet<>();

    Predicate<SMGEdgeHasValue> check = hve -> hve.getOffset() != nfo;
    getSubSmgOf(startObject, check, pSmg, valuesOfSubSmg1, objectsOfSubSmg1);
    getSubSmgOf(nextObject, check, pSmg, valuesOfSubSmg2, objectsOfSubSmg2);

    objectsOfSubSmg1.remove(startObject);
    objectsOfSubSmg2.remove(nextObject);

    // TODO Investigate, is this okay?
    nonSharedValues2.remove(pValue);

    // Third, calculate if the respective nfo restricted subsmgs are only reachable from their
    // candidate objects
    if (!isSubSmgSeperate(
        nonSharedObject1, nonSharedValues1, pSmg, objectsOfSubSmg1, valuesOfSubSmg1, startObject)) {
      isSubSmgSeperate(
          nonSharedObject1, nonSharedValues1, pSmg, objectsOfSubSmg1, valuesOfSubSmg1, startObject);
      return;
    }

    if (!isSubSmgSeperate(
        nonSharedObject2, nonSharedValues2, pSmg, objectsOfSubSmg2, valuesOfSubSmg2, nextObject)) {
      isSubSmgSeperate(
          nonSharedObject2, nonSharedValues2, pSmg, objectsOfSubSmg2, valuesOfSubSmg2, nextObject);
      return;
    }

    // check if the sequence is uninterrupted
    for (SMGEdgePointsTo pte : SMGUtils.getPointerToThisObject(startObject, pSmg)) {
      if (pte.getOffset() != candidate.getShape().getHfo()) {
        if (!nonSharedValues1.contains(pte.getValue())) {
          return;
        }
      }
    }

    for (SMGEdgePointsTo pte : SMGUtils.getPointerToThisObject(nextObject, pSmg)) {
      if (pte.getOffset() != candidate.getShape().getHfo()) {
        if (!nonSharedValues2.contains(pte.getValue())) {
          return;
        }
      } else {

        /* Nothing besides the one link from the prev object pointer may
         * point to the next object in a sll
         */
        Iterable<SMGEdgeHasValue> prevs =
            pSmg.getHVEdges(SMGEdgeHasValueFilter.valueFilter(pte.getValue()));

        if (Iterables.size(prevs) != 1) {
          return;
        }
      }
    }

    pProgress.updateProgress(pPrevCandidate, candidate, join.getStatus(), /* irrelevant */ true);
  }
}
