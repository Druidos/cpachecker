// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.common.collect.PersistentSortedMap;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.counterexample.Address;
import org.sosy_lab.cpachecker.core.counterexample.Address.BitPreciseConcreteAddress;
import org.sosy_lab.cpachecker.core.counterexample.Address.ConcreteAddress;
import org.sosy_lab.cpachecker.core.counterexample.AssumptionToEdgeAllocator;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteState;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteStatePath;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteStatePath.ConcreteStatePathNode;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteStatePath.IntermediateConcreteState;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteStatePath.SingleConcreteState;
import org.sosy_lab.cpachecker.core.counterexample.IDExpression;
import org.sosy_lab.cpachecker.core.counterexample.LeftHandSide;
import org.sosy_lab.cpachecker.core.counterexample.Memory;
import org.sosy_lab.cpachecker.cpa.smg.graphs.UnmodifiableCLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgePointsTo;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectKind;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.value.refiner.ConcreteErrorPathAllocator;
import org.sosy_lab.cpachecker.util.Pair;

public class SMGConcreteErrorPathAllocator extends ConcreteErrorPathAllocator<SMGState> {

  // this analysis puts every object in the same heap
  private static final String MEMORY_NAME = "SMG_Analysis_Heap";
  private PersistentSortedMap<BitPreciseConcreteAddress, Object> pPrevMap =
      PathCopyingPersistentTreeMap.of();

  public SMGConcreteErrorPathAllocator(AssumptionToEdgeAllocator pAssumptionToEdgeAllocator) {
    super(SMGState.class, pAssumptionToEdgeAllocator);
  }

  @Override
  protected ConcreteStatePath createConcreteStatePath(List<Pair<SMGState, List<CFAEdge>>> pPath) {

    List<ConcreteStatePathNode> result = new ArrayList<>();

    // Until SMGObjects are comparable for persistant maps, this object is mutable
    // and depends on side effects
    SMGObjectAddressMap variableAddresses = new SMGObjectAddressMap();

    SMGState prevState = null;

    for (Pair<SMGState, List<CFAEdge>> edgeStatePair : pPath) {

      SMGState pSMGState = edgeStatePair.getFirst();
      List<CFAEdge> edges = edgeStatePair.getSecond();

      if (edges.size() > 1) {
        Iterator<CFAEdge> it = Lists.reverse(edges).iterator();
        List<SingleConcreteState> intermediateStates = new ArrayList<>();
        Set<CLeftHandSide> alreadyAssigned = new HashSet<>();
        while (it.hasNext()) {
          CFAEdge innerEdge = it.next();
          ConcreteState state =
              createConcreteState(
                  variableAddresses, pSMGState, prevState, alreadyAssigned, innerEdge);

          // intermediate edge
          if (it.hasNext()) {
            intermediateStates.add(new IntermediateConcreteState(innerEdge, state));

            // last edge of (dynamic) multi edge
          } else {
            result.addAll(Lists.reverse(intermediateStates));
            result.add(new SingleConcreteState(innerEdge, state));
          }
        }

        // a normal edge, no special handling required
      } else {
        result.add(
            new SingleConcreteState(
                Iterables.getOnlyElement(edges),
                new ConcreteState(
                    ImmutableMap.of(),
                    allocateAddresses(pSMGState, prevState, variableAddresses),
                    variableAddresses.getAddressMap(),
                    exp -> MEMORY_NAME)));
      }
      prevState = pSMGState;
    }

    return new ConcreteStatePath(result);
  }

  private ConcreteState createConcreteState(
      SMGObjectAddressMap variableAddresses,
      SMGState pSMGState,
      SMGState pPrevState,
      Set<CLeftHandSide> alreadyAssigned,
      CFAEdge innerEdge) {
    ConcreteState state;

    // We know only values for LeftHandSides that have not yet been assigned.
    if (allValuesForLeftHandSideKnown(innerEdge, alreadyAssigned)) {
      state =
          new ConcreteState(
              ImmutableMap.of(),
              allocateAddresses(pSMGState, pPrevState, variableAddresses),
              variableAddresses.getAddressMap(),
              exp -> MEMORY_NAME);
    } else {
      state = ConcreteState.empty();
    }

    // add handled edges to alreadyAssigned list if necessary
    if (innerEdge.getEdgeType() == CFAEdgeType.StatementEdge) {
      CStatement stmt = ((CStatementEdge) innerEdge).getStatement();

      if (stmt instanceof CAssignment) {
        CLeftHandSide lhs = ((CAssignment) stmt).getLeftHandSide();
        alreadyAssigned.add(lhs);
      }
    }

    return state;
  }

  private boolean allValuesForLeftHandSideKnown(
      CFAEdge pCfaEdge, Set<CLeftHandSide> pAlreadyAssigned) {

    if (pCfaEdge.getEdgeType() == CFAEdgeType.DeclarationEdge) {
      return isDeclarationValueKnown((CDeclarationEdge) pCfaEdge, pAlreadyAssigned);
    } else if (pCfaEdge.getEdgeType() == CFAEdgeType.StatementEdge) {
      return isStatementValueKnown((CStatementEdge) pCfaEdge, pAlreadyAssigned);
    }

    return false;
  }

  private boolean isStatementValueKnown(
      CStatementEdge pCfaEdge, Set<CLeftHandSide> pAlreadyAssigned) {

    CStatement stmt = pCfaEdge.getStatement();

    if (stmt instanceof CAssignment) {
      CLeftHandSide leftHandSide = ((CAssignment) stmt).getLeftHandSide();

      return isLeftHandSideValueKnown(leftHandSide, pAlreadyAssigned);
    }

    return false;
  }

  private Map<String, Memory> allocateAddresses(
      SMGState pSMGState, SMGState pPrevState, SMGObjectAddressMap pAdresses) {
    PersistentMap<BitPreciseConcreteAddress, Object> values =
        createHeapValues(pSMGState, pPrevState, pAdresses);
    return ImmutableMap.of(MEMORY_NAME, new Memory(MEMORY_NAME, values));
  }

  private PersistentSortedMap<BitPreciseConcreteAddress, Object> createHeapValues(
      SMGState pSMGState, SMGState pPrevState, SMGObjectAddressMap pAdresses) {

    PersistentSortedMap<BitPreciseConcreteAddress, Object> result = pPrevMap;
    if (pPrevState == null) {
      pPrevState = pSMGState.copyOf();
      pPrevState.clearValues();
    }

    for (SMGEdgeHasValue hvEdge :
        pSMGState.getHeap().getHVEdges().remove(pPrevState.getHeap().getHVEdges())) {

      SMGValue symbolicValue = hvEdge.getValue();
      BigInteger value = null;

      if (symbolicValue.isZero()) {
        value = BigInteger.ZERO;
      } else if (pSMGState.getHeap().isPointer(symbolicValue)) {
        SMGEdgePointsTo pointer = pSMGState.getHeap().getPointer(symbolicValue);

        // TODO ugly, use common representation
        value =
            pAdresses
                .calculateAddress(pointer.getObject(), pointer.getOffset(), pSMGState)
                .getAddressValue();
      } else if (pSMGState.isExplicit(symbolicValue)) {
        value = BigInteger.valueOf(pSMGState.getExplicit(symbolicValue).getAsLong());
      } else {
        continue;
      }

      BitPreciseConcreteAddress address =
          pAdresses.calculateAddress(hvEdge.getObject(), hvEdge.getOffset(), pSMGState);
      result = result.putAndCopy(address, value);
    }

    for (SMGEdgeHasValue hvEdge :
        pPrevState.getHeap().getHVEdges().remove(pSMGState.getHeap().getHVEdges())) {
      ConcreteAddress address =
          pAdresses.calculateAddress(hvEdge.getObject(), hvEdge.getOffset(), pSMGState);
      result = result.removeAndCopy(address);
    }

    pPrevMap = result;
    return result;
  }

  public static class SMGObjectAddressMap {

    private final Map<SMGObject, BitPreciseConcreteAddress> objectAddressMap = new HashMap<>();
    private BitPreciseConcreteAddress nextAlloc =
        BitPreciseConcreteAddress.valueOf(BigInteger.valueOf(1000));
    private final Map<LeftHandSide, BitPreciseConcreteAddress> variableAddressMap = new HashMap<>();

    public BitPreciseConcreteAddress calculateAddress(
        SMGObject pObject, long pOffset, SMGState pSMGState) {

      // Create a new base address for the object if necessary
      if (!objectAddressMap.containsKey(pObject)) {
        objectAddressMap.put(pObject, nextAlloc);
        IDExpression lhs = createIDExpression(pSMGState.getHeap(), pObject);
        if (lhs != null) {
          variableAddressMap.put(lhs, nextAlloc);
        }
        BigInteger objectSize = BigInteger.valueOf(pObject.getSize());

        BigInteger nextAllocOffset =
            nextAlloc.getBitPreciseAddressValue().add(objectSize).add(BigInteger.valueOf(8));

        nextAlloc = nextAlloc.addOffset(nextAllocOffset);
      }

      return objectAddressMap.get(pObject).addOffset(BigInteger.valueOf(pOffset));
    }

    public Map<LeftHandSide, Address> getAddressMap() {
      return ImmutableMap.copyOf(variableAddressMap);
    }
  }

  private static IDExpression createIDExpression(UnmodifiableCLangSMG smg, SMGObject pObject) {

    if (pObject.getKind() == SMGObjectKind.STRING_LITERAL) {
      return null;
    }

    if (smg.getGlobalObjects().containsValue(pObject)) {
      // TODO Breaks if label is changed
      return new IDExpression(pObject.getLabel());
    }

    for (CLangStackFrame frame : smg.getStackFrames()) {
      if (frame.getVariables().containsValue(pObject)) {
        // TODO Breaks if label is changed
        return new IDExpression(pObject.getLabel(), frame.getFunctionDeclaration().getName());
      }
    }

    return null;
  }
}
