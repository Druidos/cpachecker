// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;

/** Class for representation of casting values to different types for SMG predicate relations */
public class SMGType {
  private final ImmutableList<Long> castedSize; // sequence of cast
  private final ImmutableList<Boolean> castedSigned;
  private final long originSize;
  private final boolean originSigned;

  private SMGType(long pCastedSize, boolean pCastedSigned, long pOriginSize, boolean pOriginSigned) {
    castedSize = ImmutableList.of(pCastedSize);
    castedSigned = ImmutableList.of(pCastedSigned);
    originSize = pOriginSize;
    originSigned = pOriginSigned;
  }


  public SMGType(long pCastedSize, boolean pSigned) {
    this(pCastedSize, pSigned, pCastedSize, pSigned);
  }

  public SMGType(SMGType pCastedType, SMGType pOriginType) {

    List<Long> newCastedSize = new ArrayList<>(pOriginType.getCastedSize());
    newCastedSize.add(pCastedType.getOriginSize());
    newCastedSize.addAll(pCastedType.getCastedSize());

    List<Boolean> newCastedSigned = new ArrayList<>(pOriginType.getCastedSigned());
    newCastedSigned.add(pCastedType.isOriginSigned());
    newCastedSigned.addAll(pCastedType.getCastedSigned());

    originSize = pOriginType.getOriginSize();
    originSigned = pOriginType.isOriginSigned();
    castedSize = ImmutableList.copyOf(newCastedSize);
    castedSigned = ImmutableList.copyOf(newCastedSigned);
  }

  public static SMGType constructSMGType(
      CType pType, SMGState pState, CFAEdge pEdge, SMGExpressionEvaluator smgExpressionEvaluator)
      throws UnrecognizedCodeException {
    boolean isSigned = false;
    if (pType instanceof CSimpleType) {
      isSigned = pState.getHeap().getMachineModel().isSigned((CSimpleType) pType);
    }
    long size = smgExpressionEvaluator.getBitSizeof(pEdge, pType, pState);
    return new SMGType(size, isSigned);
  }

  public ImmutableList<Long> getCastedSize() {
    return castedSize;
  }

  public Long getCastedSizeLast() {
    return castedSize.get(castedSize.size() - 1);
  }

  public ImmutableList<Boolean> getCastedSigned() {
    return castedSigned;
  }

  public Boolean getCastedSignedLast() {
    return castedSigned.get(castedSigned.size() - 1);
  }

  public long getOriginSize() {
    return originSize;
  }

  public boolean isOriginSigned() {
    return originSigned;
  }

  @Override
  public String toString() {
    return String.format(
        "CAST from '%ssigned %d bit' to %s %s",
        originSigned ? "" : "un", originSize, castedSigned, castedSize);
  }
}
