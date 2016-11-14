/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.smg;

import com.google.common.base.Function;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.smg.SMGCPA.SMGExportLevel;
import org.sosy_lab.cpachecker.cpa.smg.objects.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGMemoryPath;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGPrecision;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;


public class SMGPrecisionAdjustment implements PrecisionAdjustment, StatisticsProvider {

  // statistics
  final StatCounter abstractions    = new StatCounter("Number of abstraction computations");
  final StatTimer totalAbstraction  = new StatTimer("Total time for abstraction computation");

  private final Statistics statistics;
  private final LogManager logger;
  private final SMGExportDotOption exportOptions;


  public SMGPrecisionAdjustment(LogManager pLogger, SMGExportDotOption pExportOptions) {

    logger = pLogger;
    exportOptions = pExportOptions;

    statistics = new Statistics() {
      @Override
      public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {

        StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(pOut);
        writer.put(abstractions);
        writer.put(totalAbstraction);
      }

      @Override
      public String getName() {
        return SMGPrecisionAdjustment.this.getClass().getSimpleName();
      }
    };

  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(AbstractState pState, Precision pPrecision,
      UnmodifiableReachedSet pStates, Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {

    return prec((SMGState) pState, (SMGPrecision) pPrecision,
        AbstractStates.extractStateByType(pFullState, LocationState.class).getLocationNode());
  }

  public Optional<PrecisionAdjustmentResult> prec(SMGState pState, SMGPrecision pPrecision,
      CFANode pLocation) throws CPAException {

    boolean allowsFieldAbstraction = pPrecision.useFieldAbstraction();
    boolean allowsHeapAbstraction =
        pPrecision.useHeapAbstractionOnNode(pLocation);
    boolean allowsStackAbstraction = pPrecision.useStackAbstraction();
    boolean forgetDeadVariables = pPrecision.forgetDeadVariables();

    if (!allowsFieldAbstraction && !allowsHeapAbstraction && !allowsStackAbstraction
        && !forgetDeadVariables) {
      return Optional.of(PrecisionAdjustmentResult.create(pState, pPrecision, Action.CONTINUE));
      }

    totalAbstraction.start();

    SMGState result = pState;
    SMGState newState = pState.createSuccessor();

    if (forgetDeadVariables) {
      Map<MemoryLocation, SMGRegion> stackVars = newState.getStackVariables();
      Set<MemoryLocation> deadVars =
          pPrecision.getDeadVariablesOnLocation(pLocation, stackVars.keySet());
      Map<MemoryLocation, SMGRegion> deadVarsMap = new HashMap<>(deadVars.size());

      deadVars.forEach((MemoryLocation pDeadVar) -> {
        deadVarsMap.put(pDeadVar, stackVars.get(pDeadVar));
      });

      deadVars = newState.filterLiveReference(deadVarsMap);

      boolean liveVarChange = false;

      for (MemoryLocation deadVar : deadVars) {
        SMGStateInformation info = newState.forgetStackVariable(deadVar);
        liveVarChange = !info.equals(SMGStateInformation.of());
      }

      if(pPrecision.forgetNonRelevantVariables()) {

        Set<SMGEdgeHasValue> nonRelevantFields = newState.getNonRelevantFields(pPrecision);

        for (SMGEdgeHasValue hve : nonRelevantFields) {
          newState.forget(hve);
          liveVarChange = true;
        }
      }

      if (liveVarChange) {
        String name =
            String.format("%03d-%03d-after-forgetting-dead-variables-", result.getId(),
                newState.getId());
        String description = "after-forgetting-dead-variables-" + result.getId();
        SMGUtils.plotWhenConfigured(name, newState, description, logger,
            SMGExportLevel.EVERY, exportOptions);

        result = newState;
        logger.log(Level.ALL, "Precision adjustment on node ", pLocation.getNodeNumber(),
            " with result state id: ", result.getId());
      }
    }

    if (allowsStackAbstraction) {
      Set<MemoryLocation> stackVariables = pPrecision.getTrackedStackVariablesOnNode(pLocation);
      boolean stackAbstractionChange = newState.forgetNonTrackedStackVariables(stackVariables);

      if (stackAbstractionChange) {
        String name =
            String.format("%03d-%03d-after-stack-abstraction", result.getId(), newState.getId());
        String description = "after-stack-abstraction-of-smg-" + result.getId();
        SMGUtils.plotWhenConfigured(name, newState, description, logger,
            SMGExportLevel.EVERY, exportOptions);

        result = newState;
        logger.log(Level.ALL, "Precision adjustment on node ", pLocation.getNodeNumber(), " with result state id: ", result.getId());
      }
    }

    if (allowsFieldAbstraction) {

      Set<SMGMemoryPath> mempaths = pPrecision.getTrackedMemoryPathsOnNode(pLocation);
      boolean fieldAbstractionChange = newState.forgetNonTrackedHve(mempaths);

      if (fieldAbstractionChange) {
        String name = String.format("%03d-%03d-after-field-abstraction", result.getId(), newState.getId());
        String description = "after-field-abstraction-of-smg-" + result.getId();
        SMGUtils.plotWhenConfigured(name, newState, description, logger,
            SMGExportLevel.EVERY, exportOptions);

        result = newState;
        logger.log(Level.ALL, "Precision adjustment on node ", pLocation.getNodeNumber(),
             " with result state id: ", result.getId());
      }
    }

    if (allowsHeapAbstraction) {

      boolean heapAbstractionChange =
          newState.executeHeapAbstraction(pPrecision.getAbstractionBlocks(pLocation), pPrecision.getHeapAbsThreshold(), !pPrecision.useFieldAbstraction());

      if (heapAbstractionChange) {

        String name =
            String.format("%03d-%03d-after-heap-abstraction", result.getId(), newState.getId());
        String description = "after-heap-abstraction-of-smg-" + result.getId();
        SMGUtils.plotWhenConfigured(name, result, description, logger,
            SMGExportLevel.EVERY, exportOptions);
        logger.log(Level.ALL, "Heap abstraction on node ", pLocation.getNodeNumber(),
            " with state id: ", pState.getId());
        String name2 = String.format("%03d-before-heap-abstraction", result.getId());
        SMGDebugExporter.dumpPlot("node" + pLocation.getNodeNumber() + "_" + name2, result);
        SMGDebugExporter.dumpPlot("node" + pLocation.getNodeNumber() + "_" + name, newState);
        result = newState;
      }
    }

    totalAbstraction.stop();
    abstractions.inc();
    return Optional.of(PrecisionAdjustmentResult.create(result, pPrecision, Action.CONTINUE));
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
  }
}