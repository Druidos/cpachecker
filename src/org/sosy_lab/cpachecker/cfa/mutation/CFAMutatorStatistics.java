// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.mutation;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.PrintStream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.defaults.MultiStatistics;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatTimerWithMoreOutput;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

public class CFAMutatorStatistics extends MultiStatistics {
  private static class FirstLastStatInt extends StatInt {
    private int first;
    private int last;

    public FirstLastStatInt(StatKind pMainStatisticKind, String pTitle) {
      super(pMainStatisticKind, pTitle);
    }

    @Override
    public void setNextValue(int pNewValue) {
      if (getUpdateCount() == 0) {
        first = pNewValue;
      }
      last = pNewValue;
      super.setNextValue(pNewValue);
    }

    public int getFirstValue() {
      Preconditions.checkState(getUpdateCount() > 0);
      return first;
    }

    public int getLastValue() {
      Preconditions.checkState(getUpdateCount() > 0);
      return last;
    }
  }

  private final StatCounter totalRounds = new StatCounter("Total count of mutation rounds");

  private final FirstLastStatInt globalDeclsCount =
      new FirstLastStatInt(StatKind.AVG, "count of global declarations");
  private final FirstLastStatInt localNodesCount =
      new FirstLastStatInt(StatKind.AVG, "count of nodes in function CFAs");
  private final FirstLastStatInt localEdgesCount =
      new FirstLastStatInt(StatKind.AVG, "count of edges in function CFAs");
  private final FirstLastStatInt localFunctionsCount =
      new FirstLastStatInt(StatKind.AVG, "count of functions in function CFAs");
  private final FirstLastStatInt localCyclomatic =
      new FirstLastStatInt(StatKind.AVG, "cyclomatic complexity of function CFAs");

  private final FirstLastStatInt fullNodesCount =
      new FirstLastStatInt(StatKind.AVG, "count of nodes in full CFA");
  private final FirstLastStatInt fullEdgesCount =
      new FirstLastStatInt(StatKind.AVG, "count of edges in full CFA");
  private final FirstLastStatInt fullFunctionsCount =
      new FirstLastStatInt(StatKind.AVG, "count of functions in full CFA");
  private final FirstLastStatInt fullCyclomatic =
      new FirstLastStatInt(StatKind.AVG, "cyclomatic complexity of full CFA");

  private final StatTimerWithMoreOutput preparationTimer =
      new StatTimerWithMoreOutput("time for preparations");
  private final StatTimerWithMoreOutput mutationTimer =
      new StatTimerWithMoreOutput("time for mutations");
  private final StatTimerWithMoreOutput aftermathTimer =
      new StatTimerWithMoreOutput("time for rollbacks");
  private final StatTimerWithMoreOutput totalMutatorTimer =
      new StatTimerWithMoreOutput("Total time for CFA mutator");
  private final StatTimerWithMoreOutput processCfaTimer =
      new StatTimerWithMoreOutput("time for CFA creation from function CFAs");
  private final StatTimerWithMoreOutput resetCfaTimer =
      new StatTimerWithMoreOutput("time for reverting CFA to function CFAs");
  private final StatTimerWithMoreOutput exportTimer =
      new StatTimerWithMoreOutput("time for CFA export");

  private final StatTimer originalTime = new StatTimer("time for original analysis run");
  private final StatTimerWithMoreOutput afterMutations =
      new StatTimerWithMoreOutput("total time for analysis after mutations");
  private final StatTimerWithMoreOutput afterRollbacks =
      new StatTimerWithMoreOutput("total time for analysis after rollbacks");
  private final StatTimerWithMoreOutput feasibilityCheck =
      new StatTimerWithMoreOutput("total time for feasibility check after verdict FALSE");

  private final ImmutableMap<AnalysisOutcome, StatCounter> outcomesCounters =
      ImmutableMap.of(
          AnalysisOutcome.VERDICT_TRUE,
          new StatCounter("count of True"),
          AnalysisOutcome.VERDICT_FALSE,
          new StatCounter("count of False"),
          AnalysisOutcome.SAME_VERDICT_FALSE,
          new StatCounter("  count of original False"),
          AnalysisOutcome.FAILURE_BECAUSE_OF_EXCEPTION,
          new StatCounter("count of exceptions"),
          AnalysisOutcome.FAILURE_BECAUSE_OF_SAME_EXCEPTION,
          new StatCounter("  count of original exceptions"),
          AnalysisOutcome.VERDICT_UNKNOWN_BECAUSE_OF_TIMEOUT,
          new StatCounter("count of timeouts"),
          AnalysisOutcome.ANOTHER_VERDICT_UNKNOWN,
          new StatCounter("count of other unknowns"));
  private final ImmutableMap<DDResultOfARun, StatCounter> ddResultsCounters =
      ImmutableMap.of(
          DDResultOfARun.MAXIMIZATION_PROPERTY_HOLDS,
          new StatCounter("count of good outcomes (passes)"),
          DDResultOfARun.MINIMIZATION_PROPERTY_HOLDS,
          new StatCounter("count of bad outcomes (fails)"),
          DDResultOfARun.NEITHER_PROPERTY_HOLDS,
          new StatCounter("count of unresolved outcomes"));
  private final ImmutableMap<DDResultOfARun, ImmutableSet.Builder<AnalysisOutcome>>
      ddResultsToOccuredOutcomes =
          ImmutableMap.of(
              DDResultOfARun.MAXIMIZATION_PROPERTY_HOLDS,
              new ImmutableSet.Builder<AnalysisOutcome>(),
              DDResultOfARun.MINIMIZATION_PROPERTY_HOLDS,
              new ImmutableSet.Builder<AnalysisOutcome>(),
              DDResultOfARun.NEITHER_PROPERTY_HOLDS,
              new ImmutableSet.Builder<AnalysisOutcome>());

  private Statistics lastRoundStats = null;
  private boolean lastRoundIsOriginalRound;

  public void setLastRoundStatistics(Statistics pStats, boolean pOriginalRound) {
    lastRoundStats = pStats;
    lastRoundIsOriginalRound = pOriginalRound;
  }

  @Override
  public @Nullable String getName() {
    return null;
  }

  public CFAMutatorStatistics(LogManager pLogger) {
    super(pLogger);
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
    if (lastRoundStats != null) {
      if (!lastRoundIsOriginalRound) {
        pOut.println("Statistics for last round");
        pOut.println("=========================");
      }
      StatisticsUtils.printStatistics(lastRoundStats, pOut, logger, pResult, pReached);
      StatisticsUtils.writeOutputFiles(lastRoundStats, logger, pResult, pReached);
      pOut.println();
    } else {
      pOut.print("[ Last round provided no statistics. ]");
    }

    if (lastRoundIsOriginalRound) {
      // nothing was mutated -- nothing useful to print
      return;
    }

    pOut.println("CFA mutation statistics");
    pOut.println("=======================");

    put(pOut, 0, totalRounds);
    for (DDResultOfARun ddResult :
        ImmutableList.of(
            DDResultOfARun.MINIMIZATION_PROPERTY_HOLDS,
            DDResultOfARun.MAXIMIZATION_PROPERTY_HOLDS,
            DDResultOfARun.NEITHER_PROPERTY_HOLDS)) {
      put(pOut, 1, ddResultsCounters.get(ddResult));
      ddResultsToOccuredOutcomes
          .get(ddResult)
          .build()
          .forEach(outcome -> put(pOut, 2, outcomesCounters.get(outcome)));
    }

    put(pOut, 0, totalMutatorTimer);
    put(pOut, 1, preparationTimer);
    pOut.println("  additionally,");
    put(pOut, 1, mutationTimer);
    put(pOut, 1, aftermathTimer);
    pOut.println("Time outside CFAMutator:");
    if (originalTime.getUpdateCount() > 0) {
      put(pOut, 1, originalTime);
      put(pOut, 1, afterMutations);
      if (afterRollbacks.getUpdateCount() > 0) {
        put(pOut, 1, afterRollbacks);
      }
      if (feasibilityCheck.getUpdateCount() > 0) {
        put(pOut, 1, feasibilityCheck);
      }
    }
    put(pOut, 1, processCfaTimer);
    put(pOut, 1, resetCfaTimer);
    put(pOut, 1, exportTimer);
    pOut.println();

    pOut.println("Statistics for function CFAs");
    pOut.println("----------------------------");
    put(pOut, 0, localNodesCount);
    put(pOut, 0, localEdgesCount);
    put(pOut, 0, globalDeclsCount);
    put(pOut, 0, localFunctionsCount);
    put(pOut, 0, localCyclomatic);
    pOut.println();

    pOut.println("Statistics for full CFA");
    pOut.println("-----------------------");
    put(pOut, 0, fullNodesCount);
    put(pOut, 0, fullEdgesCount);
    put(pOut, 0, fullFunctionsCount);
    put(pOut, 0, fullCyclomatic);

    super.printStatistics(pOut, pResult, pReached);
  }

  public void put(PrintStream pTarget, int pIndent, FirstLastStatInt pValue) {
    super.put(pTarget, pIndent, pValue);
    super.put(
        pTarget, pIndent + 1, pValue.getTitle() + " before mutations", pValue.getFirstValue());
    super.put(pTarget, pIndent + 1, pValue.getTitle() + " after mutations", pValue.getLastValue());
  }

  public void setCfaStats(FunctionCFAsWithMetadata pLocalCfa) {
    localNodesCount.setNextValue(pLocalCfa.getCFANodes().size());
    globalDeclsCount.setNextValue(pLocalCfa.getGlobalDeclarations().size());
    // cc = decision points - exit points + 2
    int cc = 2 * pLocalCfa.getFunctions().size();
    int edges = 0;
    for (CFANode n : pLocalCfa.getCFANodes().values()) {
      edges += n.getNumLeavingEdges();
      // += 1 for decision point (2 leaving edges)
      // -= 1 for terminal point (0 leaving edges)
      cc += n.getNumLeavingEdges() - 1;
    }
    localEdgesCount.setNextValue(edges);
    localFunctionsCount.setNextValue(pLocalCfa.getFunctions().size());
    localCyclomatic.setNextValue(cc);
    // cfa will be constucted fully between these calls
    processCfaTimer.start();
  }

  public void setCfaStats(CFA pCfa) {
    // cfa was constucted fully between these calls
    processCfaTimer.stop();

    fullNodesCount.setNextValue(pCfa.getAllNodes().size());
    int cc = 0;
    int edges = 0;
    for (CFANode n : pCfa.getAllNodes()) {
      edges += n.getNumLeavingEdges();
      // nodes can be unreachable via local edges, dont count these
      if (n instanceof FunctionExitNode) {
        if (((FunctionExitNode) n).getEntryNode().getNumEnteringEdges() == 0) {
          // +2 for function not called by anyone
          cc += 2;
        }
        // function exits can have any amount of leaving edges
        if (n.getNumEnteringEdges() > 0 && n.getNumLeavingEdges() == 0) {
          // count only reachable exits
          // -1 for exit not leading anywhere
          cc -= 1;
        }
      } else if (n.getNumEnteringEdges() > 0) {
        // dont count nodes that are not reachable via local edges
        // += 1 for decision point (2 leaving edges)
        // -= 1 for terminal point (0 leaving edges)
        cc += n.getNumLeavingEdges() - 1;
      }
    }
    fullEdgesCount.setNextValue(edges);
    fullFunctionsCount.setNextValue(pCfa.getAllFunctionHeads().size());
    fullCyclomatic.setNextValue(cc);
  }

  public void startExport() {
    exportTimer.start();
  }

  public void stopExport() {
    exportTimer.stop();
  }

  public TimeSpan getMaxExportTime() {
    return exportTimer.getMaxTime();
  }

  public void startPreparations() {
    totalMutatorTimer.start();
    preparationTimer.start();
  }

  public void startCfaReset() {
    resetCfaTimer.start();
  }

  public void stopCfaReset() {
    resetCfaTimer.stop();
  }

  public void stopPreparations() {
    preparationTimer.stop();
    totalMutatorTimer.stop();
  }

  public void startMutation() {
    totalMutatorTimer.start();
    mutationTimer.start();
    totalRounds.inc();
  }

  public void stopMutation() {
    mutationTimer.stop();
    totalMutatorTimer.stop();
  }

  public void startAftermath(AnalysisOutcome pOutcome, DDResultOfARun pDdResult) {
    totalMutatorTimer.start();
    aftermathTimer.start();

    ddResultsToOccuredOutcomes.get(pDdResult).add(pOutcome);
    ddResultsCounters.get(pDdResult).inc();

    outcomesCounters.get(pOutcome).inc();

    if (pOutcome == AnalysisOutcome.SAME_VERDICT_FALSE) {
      outcomesCounters.get(AnalysisOutcome.VERDICT_FALSE).inc();

    } else if (pOutcome == AnalysisOutcome.FAILURE_BECAUSE_OF_SAME_EXCEPTION) {
      outcomesCounters.get(AnalysisOutcome.FAILURE_BECAUSE_OF_EXCEPTION).inc();
    }
  }

  public void stopAftermath() {
    aftermathTimer.stop();
    totalMutatorTimer.stop();
  }

  public void startFeasilityCheck() {
    feasibilityCheck.start();
  }

  public void stopFeasibilityCheck() {
    feasibilityCheck.stop();
  }

  public void startOriginalTime() {
    originalTime.start();
  }

  public void stopOriginalTime() {
    originalTime.stop();
  }

  public TimeSpan getOriginalTime() {
    return originalTime.getConsumedTime();
  }

  public void startTimeAfterMutations() {
    afterMutations.start();
  }

  public void stopTimeAfterMutations() {
    afterMutations.stop();
  }

  public void startTimeAfterRollback() {
    afterRollbacks.start();
  }

  public void stopTimeAfterRollback() {
    afterRollbacks.stop();
  }
}
