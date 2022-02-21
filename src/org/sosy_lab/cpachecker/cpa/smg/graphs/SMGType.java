// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs;

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
  private final List<Long> castedSize; // sequence of cast
  private final List<Boolean> castedSigned;
  private final long originSize;
  private final boolean originSigned;

  private SMGType(long pCastedSize, boolean pCastedSigned, long pOriginSize, boolean pOriginSigned) {
    castedSize = new ArrayList<>();
    castedSize.add(pCastedSize);
    castedSigned = new ArrayList<>();
    castedSigned.add(pCastedSigned);
    originSize = pOriginSize;
    originSigned = pOriginSigned;
  }

  private SMGType(List<Long> pCastedSize, List<Boolean> pCastedSigned, long pOriginSize, boolean pOriginSigned) {
    castedSize = pCastedSize;
    castedSigned = pCastedSigned;
    originSize = pOriginSize;
    originSigned = pOriginSigned;
  }

  public SMGType(long pCastedSize, boolean pSigned) {
    this(pCastedSize, pSigned, pCastedSize, pSigned);
  }

  public SMGType(SMGType pCastedType, SMGType pOriginType) {
    this(
        pOriginType.getCastedSize(),
        pOriginType.getCastedSigned(),
        pOriginType.getOriginSize(),
        pOriginType.isOriginSigned());
    this.castedSize.add(pCastedType.getOriginSize());
    this.castedSigned.add(pCastedType.isOriginSigned());
    this.castedSize.addAll(pCastedType.getCastedSize());
    this.castedSigned.addAll(pCastedType.getCastedSigned());
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

  public List<Long> getCastedSize() {
    return castedSize;
  }

  public Long getCastedSizeLast() { return castedSize.get(castedSize.size() - 1); }

  public List<Boolean> getCastedSigned() {
    return castedSigned;
  }

  public Boolean getCastedSignedLast() { return castedSigned.get(castedSigned.size() - 1); }

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
