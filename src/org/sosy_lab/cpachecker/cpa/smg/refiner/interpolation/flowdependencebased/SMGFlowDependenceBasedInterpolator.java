/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.smg.refiner.interpolation.flowdependencebased;

import com.google.common.collect.ImmutableMap;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath.PathIterator;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.smg.SMGCPA.SMGExportLevel;
import org.sosy_lab.cpachecker.cpa.smg.SMGExportDotOption;
import org.sosy_lab.cpachecker.cpa.smg.SMGInconsistentException;
import org.sosy_lab.cpachecker.cpa.smg.SMGPrecisionAdjustment;
import org.sosy_lab.cpachecker.cpa.smg.SMGPredicateManager;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.SMGUtils;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGCEGARUtils;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGFeasibilityChecker;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGPrecision;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGStrongestPostOperator;
import org.sosy_lab.cpachecker.cpa.smg.refiner.interpolation.SMGEdgeHeapAbstractionInterpolator;
import org.sosy_lab.cpachecker.cpa.smg.refiner.interpolation.SMGInterpolant;
import org.sosy_lab.cpachecker.cpa.smg.refiner.interpolation.SMGStateInterpolant.SMGPrecisionIncrement;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException.Reason;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.predicates.BlockOperator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class SMGFlowDependenceBasedInterpolator {

  private final LogManager logger;
  private final SMGFeasibilityChecker checker;
  private final PathTemplate exportPath;
  private final SMGExportLevel smgExportLevel;
  private final SMGStrongestPostOperator strongestPostOp;
  private final SMGEdgeHeapAbstractionInterpolator heapAbstractionInterpolator;

  public SMGFlowDependenceBasedInterpolator(LogManager pLogger, SMGFeasibilityChecker pChecker,
      Configuration pConfig, CFA pCfa, SMGPredicateManager pSMGPredicateManager,
      BlockOperator pBlockOperator, PathTemplate pExportPath,
      SMGExportLevel pSmgExportLevel)
      throws InvalidConfigurationException {
    logger = pLogger;
    SMGStrongestPostOperator postOpForInterpolation =
        SMGStrongestPostOperator.getSMGStrongestPostOperatorForInterpolation(logger, pConfig, pCfa,
            pSMGPredicateManager, pBlockOperator);
    checker = new SMGFeasibilityChecker(pChecker, postOpForInterpolation);

    strongestPostOp =
        SMGStrongestPostOperator.getSMGStrongestPostOperatorForUseGraph(pLogger, pConfig, pCfa,
            pSMGPredicateManager, pBlockOperator);
    heapAbstractionInterpolator = new SMGEdgeHeapAbstractionInterpolator(logger, checker);
    exportPath = pExportPath;
    smgExportLevel = pSmgExportLevel;
  }

  public Map<ARGState, SMGInterpolant> performUseGraphBasedInterpolation(ARGPath pErrorPath, ARGState pRoot, ARGReachedSet pReached, int pInterpolationId)
      throws CPAException, InterruptedException {

    Map<ARGState, SMGInterpolant> prevInterpolants = new HashMap<>();

    SMGPrecision originalPrec = SMGCEGARUtils.extractSMGPrecision(pReached, pErrorPath.getLastState());

    SMGPrecision newPrec = SMGPrecision.createRefineablePrecision(originalPrec, true);

    int currentDependencePathcreationIndex = 0;

    do {

      currentDependencePathcreationIndex++;

      Map<ARGState, SMGInterpolant> newInterpolants =
          interpolatePath(pErrorPath, pRoot, prevInterpolants, pReached, currentDependencePathcreationIndex, pInterpolationId);

      if (newInterpolants.equals(prevInterpolants)) {

        exportInterpolants(pErrorPath, pRoot, pInterpolationId, newPrec, newInterpolants, pReached);

        throw new RefinementFailedException(
          Reason.InterpolationFailed, pErrorPath);
      }

      prevInterpolants = newInterpolants;
      newPrec = (SMGPrecision) newPrec.withIncrement(getNewPrecInc(newInterpolants));

    } while (checker.isFeasible(pErrorPath,
        AbstractStates.extractStateByType(pRoot, SMGState.class), pReached, newPrec));

    if (smgExportLevel == SMGExportLevel.EVERY) {
      exportInterpolants(pErrorPath, pRoot, pInterpolationId, newPrec, prevInterpolants, pReached);
    }

    return prevInterpolants;
  }

  private Map<CFANode, SMGPrecisionIncrement> getNewPrecInc(
      Map<ARGState, SMGInterpolant> pNewInterpolants) {

    Map<CFANode, SMGPrecisionIncrement> result = new HashMap<>();

    for (Entry<ARGState, SMGInterpolant> entry : pNewInterpolants.entrySet()) {
      CFANode location = AbstractStates.extractLocation(entry.getKey());
      SMGInterpolant interpolant = entry.getValue();

      if(result.containsKey(location)) {
        result.put(location, interpolant.getPrecisionIncrement().join(result.get(location)));
      } else {
        result.put(location, interpolant.getPrecisionIncrement());
      }
    }

    return result;
  }

  private Map<ARGState, SMGInterpolant> interpolatePath(ARGPath pErrorPath, ARGState pRoot,
      Map<ARGState, SMGInterpolant> pPrevInterpolants, ARGReachedSet pReached, int pCurrentDependencePathcreationIndex, int pInterpolationId) throws CPAException, InterruptedException {

    SMGPathDependenceBuilder builder =
        new SMGPathDependenceBuilder(logger, checker, strongestPostOp, heapAbstractionInterpolator,
            pErrorPath, pRoot, pPrevInterpolants, pReached);

    Set<SMGPathDependence> memoryDependence = builder.buildMemoryDependences();

    if (smgExportLevel == SMGExportLevel.EVERY) {
      exportPathDependence(memoryDependence, pCurrentDependencePathcreationIndex, pInterpolationId);
    }

    if(memoryDependence.size() < 2) {
      return memoryDependence.iterator().next().obtainInterpolantsBasedOnDependency();
    } else {
      return joinMemoryDepedenceInterpolants(memoryDependence);
    }
  }

  public Set<SMGPathDependence> buildMemoryDependence(ARGPath pErrorPath, ARGState pRoot,
      Map<ARGState, SMGInterpolant> pPrevInterpolants, ARGReachedSet pReached) throws CPAException, InterruptedException {

    SMGPathDependenceBuilder builder =
        new SMGPathDependenceBuilder(logger, checker, strongestPostOp, heapAbstractionInterpolator,
            pErrorPath, pRoot, pPrevInterpolants, pReached);

    Set<SMGPathDependence> memoryDependence = builder.buildMemoryDependences();
    return memoryDependence;
  }


  private Map<ARGState, SMGInterpolant> joinMemoryDepedenceInterpolants(Set<SMGPathDependence> pMemoryDependence) throws RefinementFailedException {

    Map<ARGState, SMGInterpolant> newInterpolants = null;

    for (SMGPathDependence dependence : pMemoryDependence) {
      if(newInterpolants == null) {
        newInterpolants = dependence.obtainInterpolantsBasedOnDependency();
      } else {
        Map<ARGState, SMGInterpolant> newInterpolants2 =
            dependence.obtainInterpolantsBasedOnDependency();
        Map<ARGState, SMGInterpolant> joinedInterpolants = new HashMap<>();
        joinedInterpolants.putAll(newInterpolants);

        for (Entry<ARGState, SMGInterpolant> entry : newInterpolants2.entrySet()) {
          ARGState state = entry.getKey();
          SMGInterpolant value = entry.getValue();
          if (newInterpolants.containsKey(state)) {
            SMGInterpolant oldInterpolant = newInterpolants.get(state);
            joinedInterpolants.put(state, oldInterpolant.join(value));
          } else {
            joinedInterpolants.put(state, value);
          }
        }

        newInterpolants = ImmutableMap.copyOf(joinedInterpolants);
      }
    }

    return newInterpolants;
  }

  private void exportInterpolants(Map<ARGState, SMGInterpolant> interpolants, ARGPath path,
      int pInterpolationId) {

    PathIterator it = path.fullPathIterator();

    while (it.advanceIfPossible()) {

      if(!it.isPositionWithState()) {
        continue;
      }

      if (!interpolants.containsKey(it.getAbstractState())) {
        continue;
      }

      CFAEdge edge = it.getIncomingEdge();

      if (edge == null) {
        continue;
      }

      if (edge.getEdgeType() == CFAEdgeType.BlankEdge) {
        continue;
      }

      if (edge.getEdgeType() == CFAEdgeType.DeclarationEdge) {
        CDeclarationEdge cDclEdge = (CDeclarationEdge) edge;
        CDeclaration cDcl = cDclEdge.getDeclaration();
        if (cDcl instanceof CFunctionDeclaration ||
            cDcl instanceof CTypeDeclaration) {
          continue;
        }
      }

      exportInterpolant(it, pInterpolationId,
          interpolants.get(it.getAbstractState()));
    }
  }

  private void exportInterpolants(ARGPath pErrorPath, ARGState root, int pInterpolationId,
      SMGPrecision pNewPrec, Map<ARGState, SMGInterpolant> interpolants, ARGReachedSet pReached) throws CPAException, InterruptedException {

    if (pErrorPath.size() <= 1) {
      return;
    }

    exportCFAPath(pErrorPath, pInterpolationId);
    exportInterpolants(interpolants, pErrorPath, pInterpolationId);

    PathIterator it = pErrorPath.fullPathIterator();
    SMGState initialState = AbstractStates.extractStateByType(root, SMGState.class);
    initialState = new SMGState(initialState);

    while (it.getAbstractState() != root) {
      it.advance();
    }

    initialState = adjustState(initialState, pNewPrec, it, pReached);
    exportState(initialState, it, pInterpolationId, it.getLocation(), 1, 0);

    AtomicInteger pathIdCounter = new AtomicInteger(1);
    exportStatePath(it.getSuffixInclusive(), initialState, pNewPrec, pInterpolationId, pathIdCounter.getAndIncrement(), pathIdCounter, it.getIndex(), pReached);
  }

  private void exportStatePath(ARGPath pPath, SMGState pInitialState, SMGPrecision pNewPrec, int pInterpolationId, int pathId,
      AtomicInteger pPathIdCounter, int pOriginalStartPosition, ARGReachedSet pReached) throws CPAException, InterruptedException {

    PathIterator it = pPath.fullPathIterator();
    SMGState nextState = pInitialState;

    while (it.advanceIfPossible()) {

      while (it.getIncomingEdge() == null
          && it.hasNext()) {
        it.advanceIfPossible();
      }

      CFAEdge edge = it.getIncomingEdge();

      Collection<SMGState> states = checker.getStrongestPostOp().getStrongestPost(nextState, pNewPrec, edge);

      if(states.isEmpty()) {
        break;
      }

      Iterator<SMGState> stateIt = states.iterator();
      nextState = stateIt.next();

      while(stateIt.hasNext()) {

        SMGState newInitialPathState = stateIt.next();
        int newPathId = pPathIdCounter.getAndIncrement();

        ARGPath newPath= it.getSuffixInclusive();

        if (it.isPositionWithState()) {
          newInitialPathState = adjustState(newInitialPathState, pNewPrec, it, pReached);
        }

        exportState(newInitialPathState, it, pInterpolationId, edge.getSuccessor(),
            newPathId, pOriginalStartPosition);
        exportStatePath(newPath, newInitialPathState, pNewPrec, pInterpolationId, newPathId,
            pPathIdCounter, it.getIndex() + pOriginalStartPosition, pReached);
      }

      if(it.isPositionWithState()) {
        nextState = adjustState(nextState, pNewPrec, it, pReached);
      }

      exportState(nextState, it, pInterpolationId, edge.getSuccessor(), pathId, pOriginalStartPosition);
    }
  }

  private SMGState adjustState(SMGState pState, SMGPrecision pNewPrecision, PathIterator pIterator,
      ARGReachedSet pReachedSet)
      throws CPAException {

    SMGPrecisionAdjustment precAdj =
        new SMGPrecisionAdjustment(logger, SMGExportDotOption.getNoExportInstance());

    SMGPrecision originalPrecision =
        SMGCEGARUtils.extractSMGPrecision(pReachedSet, pIterator.getAbstractState());

    SMGPrecision precision = originalPrecision.join(pNewPrecision);

    SMGState resultState = (SMGState) precAdj.prec(pState, precision, pIterator.getLocation())
        .orElse(PrecisionAdjustmentResult.create(pState, precision, Action.BREAK)).abstractState();

    return resultState;
  }

  private void exportState(SMGState pState, PathIterator pIt, int pInterpolationId, CFANode pCurrentLocation,
      int pathId, int pOriginalStartPosition) throws SMGInconsistentException {

    CFAEdge incomingEdge;

    if (pIt.hasPrevious()) {
      incomingEdge = pIt.getIncomingEdge();
    } else {
      incomingEdge = pIt.getOutgoingEdge();
    }

    if (incomingEdge.getEdgeType() == CFAEdgeType.BlankEdge) {
      return;
    }

    if (incomingEdge.getEdgeType() == CFAEdgeType.DeclarationEdge) {
      CDeclarationEdge cDclEdge = (CDeclarationEdge) incomingEdge;
      CDeclaration cDcl = cDclEdge.getDeclaration();
      if (cDcl instanceof CFunctionDeclaration ||
          cDcl instanceof CTypeDeclaration) {
        return;
      }
    }

      int pathPos = pIt.getIndex() + pOriginalStartPosition;
      String fileName = "smgInterpolant-" + pathPos + "-smg-" + pathId + ".dot";
      Path path = exportPath.getPath(pInterpolationId, fileName);
      String location = incomingEdge.toString() + " on N" + pCurrentLocation.getNodeNumber();
      pState.pruneUnreachable();
      SMGUtils.dumpSMGPlot(logger, pState, location, path);
  }

  private void exportInterpolant(PathIterator pIt, int pInterpolationId,
      SMGInterpolant interpolant) {

    if (exportPath == null) {
      return;
    }

    String fileName = "smgInterpolantpaths-" + pIt.getIndex() + ".txt";
    Path path = exportPath.getPath(pInterpolationId, fileName);

    try {
      MoreFiles.writeFile(path, Charset.defaultCharset(), interpolant.toString());
    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e,
          "Failed to write interpolation path to path " + path.toString());
    }
  }

  private void exportCFAPath(ARGPath pPath, int pInterpolationId) {

    if(exportPath == null) {
      return;
    }

    PathIterator pPathIterator = pPath.fullPathIterator();

    StringBuilder interpolationPath = new StringBuilder();

    while (pPathIterator.advanceIfPossible()) {

      CFAEdge edge = pPathIterator.getIncomingEdge();

      if (edge == null) {
        continue;
      }

      if(edge.getEdgeType() == CFAEdgeType.BlankEdge) {
        continue;
      }

      if (edge.getEdgeType() == CFAEdgeType.DeclarationEdge) {
        CDeclarationEdge cDclEdge = (CDeclarationEdge) edge;
        CDeclaration cDcl = cDclEdge.getDeclaration();
        if (cDcl instanceof CFunctionDeclaration ||
            cDcl instanceof CTypeDeclaration) {
          continue;
        }
      }

      interpolationPath.append(edge.toString());
      interpolationPath.append(" ");
      interpolationPath.append("Pos");
      interpolationPath.append(pPathIterator.getIndex() - 1);
      interpolationPath.append("->");
      interpolationPath.append(pPathIterator.getIndex());
      interpolationPath.append("\n");
    }

    Path path = exportPath.getPath(pInterpolationId, "interpolationPath.txt");

    try {
      MoreFiles.writeFile(path, Charset.defaultCharset(), interpolationPath.toString());
    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e,
          "Failed to write interpolation path to path " + path.toString());
    }
  }

  private void exportPathDependence(Set<SMGPathDependence> pMemoryDependence,
      int pCurrentDependencePathcreationIndex, int pInterpolationId) {

    if (exportPath == null) {
      return;
    }

    int pathDependenceId = 1;

    for (SMGPathDependence pathDependence : pMemoryDependence) {

      pathDependence.exportPathDependence(exportPath, pCurrentDependencePathcreationIndex,
          pInterpolationId, pathDependenceId);
      pathDependenceId++;
    }
  }
}