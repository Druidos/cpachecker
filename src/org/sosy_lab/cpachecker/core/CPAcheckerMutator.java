// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.MoreStrings;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.mutation.AnalysisOutcome;
import org.sosy_lab.cpachecker.cfa.mutation.CFAMutator;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.CexAsProgramCPAchecker;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.CounterexampleCheckAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.CounterexampleChecker;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.core.specification.Specification;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CounterexampleAnalysisFailed;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.resources.ResourceLimitChecker;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatTimerWithMoreOutput;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

public final class CPAcheckerMutator extends CPAchecker {

  static enum AnalysisRun {
    ORIGINAL,
    AFTER_MUTATION,
    FEASIBILITY_CHECK,
    AFTER_ROLLBACK,
    DEFAULT,
  }

  private CFAMutationManager cfaMutationManager;

  private final CFAMutator cfaMutator;

  public CPAcheckerMutator(
      Configuration pConfiguration,
      LogManager pLogManager,
      ShutdownManager pShutdownManager,
      ResourceLimitChecker pLimits)
      throws InvalidConfigurationException {

    super(pConfiguration, pLogManager, pShutdownManager);
    cfaMutationManager =
        new CFAMutationManager(pConfiguration, pLogManager, pShutdownManager, pLimits);
    cfaMutator = new CFAMutator(config, logger, shutdownNotifier, cfaMutationManager);

    if (getSerializedCfaFile() != null) {
      throw new InvalidConfigurationException(
          "CFA mutation needs source files to be parsed into CFA. Do not specify 'cfaMutation=true' "
              + "and loading CFA with 'analysis.serializedCfaFile' simultaneously.");
    }
  }

  @Override
  public CPAcheckerResult run(List<String> programDenotation) {
    checkArgument(!programDenotation.isEmpty());
    logger.logf(
        Level.INFO,
        "%s / %s (%s) started",
        getVersion(config),
        cfaMutator.getApproachName(),
        getJavaInformation());
    MainCFAMutationStatistics totalStats = new MainCFAMutationStatistics();
    AnalysisResult lastResult = null;

    try {
      cfaMutationManager.resetExportDirectory(AnalysisRun.ORIGINAL);
      parse(cfaMutator, programDenotation);
      totalStats.setCFACreatorStatistics(cfaMutator.getStatistics());
      cfaMutator.clearCreatorStats();

      CFA originalCfa = getCfa();
      if (originalCfa == null) {
        // invalid input files
        return asMutatorResult(null, Result.NOT_YET_STARTED, totalStats);
      }

      Specification originalSpec = getSpecification(originalCfa);

      final AnalysisResult originalResult =
          analysisRound(originalCfa, totalStats.originalTime, AnalysisRun.ORIGINAL);

      AnalysisOutcome originalOutcome = originalResult.toAnalysisOutcome(originalResult);
      if (cfaMutator.shouldReturnWithoutMutation(originalOutcome)) {
        cfaMutationManager.resetExportDirectory(AnalysisRun.DEFAULT);
        return originalResult.result;
      }

      cfaMutationManager.setSpecification(originalSpec);
      cfaMutationManager.setOriginalTime(totalStats.originalTime.getConsumedTime());

      if (cfaMutationManager.exceedsLimitsForAnalysis(
          "CFA mutation interrupted before it started to mutate the CFA")) {
        return asMutatorResult(originalResult, Result.NOT_YET_STARTED, totalStats);
      }

      // CFAMutator stores needed info from #parse,
      // so no need to pass CFA as argument in next calls

      int rollbacksInRow = 0;
      for (int round = 1; cfaMutator.canMutate(); round++) {
        logger.log(Level.INFO, "Mutation round", round);

        cfaMutationManager.resetExportDirectory(AnalysisRun.AFTER_MUTATION);
        CFA mutated = cfaMutator.mutate();
        lastResult =
            analysisRound(mutated, totalStats.afterMutations, AnalysisRun.AFTER_MUTATION);
        // TODO export intermediate results
        // XXX it is incorrect to save intermediate stats, as reached is updated?

        AnalysisOutcome lastOutcome = lastResult.toAnalysisOutcome(originalResult);

        if (cfaMutator.shouldCheckFeasibiblity(lastOutcome)) {
          if (cfaMutationManager.exceedsLimitsForFeasibility()) {
            return asMutatorResult(lastResult, Result.UNKNOWN, totalStats);
          }

          logger.log(Level.INFO, "Found a counterexample. Checking it on original program.");

          cfaMutationManager.resetExportDirectory(AnalysisRun.FEASIBILITY_CHECK);
          Feasibility errorIsFeasible = Feasibility.noFeasibleErrorFound();
          try {
            totalStats.feasibilityCheck.start();
            errorIsFeasible = isFeasible(lastResult);
          } finally {
            totalStats.feasibilityCheck.stop();
          }

          if (errorIsFeasible.isFeasible) {
            if (errorIsFeasible.foundFalse != null) {
              lastResult = errorIsFeasible.foundFalse;
            }

            logger.log(
                Level.INFO, "Found feasible error:", lastResult.result.getTargetDescription());
            return asMutatorResult(lastResult, Result.FALSE, totalStats);
          }
        }

        cfaMutationManager.resetExportDirectory(AnalysisRun.AFTER_ROLLBACK);
        CFA rollbacked = cfaMutator.setResult(lastOutcome);

        if (cfaMutationManager.exceedsLimitsForAnalysis(
            String.format("CFA mutation interrupted after %s. analysis round", round))) {
          return asMutatorResult(lastResult, Result.DONE, totalStats);
        }

        // Check that property is still preserved after rollback
        if (rollbacked == null || cfaMutationManager.getCheckAfterRollbacks() == 0) {
          // options say pass the check this time
          rollbacksInRow = 0;

        } else if (++rollbacksInRow % cfaMutationManager.getCheckAfterRollbacks() == 0) {
          logger.log(
              Level.INFO, "Running analysis after", rollbacksInRow, "mutation rollback in row");

          lastResult =
              analysisRound(rollbacked, totalStats.afterRollbacks, AnalysisRun.AFTER_ROLLBACK);
          AnalysisOutcome analysisOutcome = lastResult.toAnalysisOutcome(originalResult);
          // If analysis ended because of a global shutdown, the result may be TIMEOUT
          // otherwise, check it is expected one
          if (!shutdownNotifier.shouldShutdown()
              || analysisOutcome != AnalysisOutcome.VERDICT_UNKNOWN_BECAUSE_OF_TIMEOUT) {
            cfaMutator.verifyOutcome(analysisOutcome);
          }

          if (cfaMutationManager.exceedsLimitsForAnalysis(
              String.format(
                  "CFA mutation interrupted after rollback after %s. analysis round", round))) {
            return asMutatorResult(lastResult, Result.DONE, totalStats);
          }
        }
      }

      if (lastResult == null) {
        logger.log(Level.INFO, "There are no possible mutations for the CFA of the given program.");
        return asMutatorResult(originalResult, Result.NOT_YET_STARTED, totalStats);
      }

      logger.log(Level.INFO, "CFA mutation ended, as all possible mutations were tried.");
      return asMutatorResult(lastResult, Result.DONE, totalStats);

    } catch (InvalidConfigurationException e) {
      logger.logUserException(Level.SEVERE, e, "Invalid configuration");
      // XXX move to #analysisRound?
      // but for first round should catch here?

    } catch (InterruptedException e) {
      // CPAchecker must exit because it was asked to
      // we return normally instead of propagating the exception
      // so we can return the partial result we have so far
      logger.logUserException(Level.WARNING, e, "CFA mutation interrupted");

    } catch (ParserException e) {
      logger.logUserException(Level.SEVERE, e, "Parser error");
      // XXX is it possible?
      // TODO export previous CFA then?

    }

    if (lastResult == null) {
      return asMutatorResult(null, Result.NOT_YET_STARTED, totalStats);
    }
    return asMutatorResult(lastResult, Result.DONE, totalStats);
  }

  @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
  // run analysis, but for already stored CFA, and catch its errors
  private AnalysisResult analysisRound(CFA pCfa, StatTimer pTimer, AnalysisRun pRun)
      throws InvalidConfigurationException {

    pTimer.start();
    Throwable t = null;
    CPAchecker cpachecker = cfaMutationManager.createCpacheckerAndStartLimits(pRun);
    LogManager curLogger = cfaMutationManager.getCurrentLogger();

    try {
      cpachecker.setupMainStats();
      cpachecker.setCfa(pCfa);
      try {
        cpachecker.setupAnalysis();
      } finally {
        cpachecker.stopMainStatsCreationTimer();
      }
      cpachecker.runAnalysis();

    } catch (InterruptedException e) {
      // this round was too long
      t = e;
      logger.log(Level.INFO, "Timeout:", e.getMessage());
      curLogger.logException(Level.WARNING, e, "Analysis round interrupted");

    } catch (AssertionError
        | IllegalArgumentException
        | IllegalStateException
        | IndexOutOfBoundsException
        | ClassCastException
        | NullPointerException
        | NoSuchElementException
        | VerifyException
        | CPAException e) {
      // Expect exceptions as bugs of CPAchecker, and remember them to reproduce on a smaller CFA.
      // TODO which types exactly
      t = e;
      if (e.getStackTrace().length == 0) {
        // too many same exceptions were thrown, it's JVM optimization
        logger.logf(Level.INFO, "Recurring %s (no stack trace)", e.getClass());
        curLogger.logf(Level.WARNING, "Recurring %s (no stack trace)", e.getClass());
      } else {
        logger.logf(Level.INFO, "Exception %s: %s", e.getClass().getSimpleName(), e.getMessage());
        curLogger.logException(Level.WARNING, e, null);
      }

    } finally {
      cfaMutationManager.cancelAnalysisLimits();
      cpachecker.close();
      pTimer.stop();
    }

    CPAcheckerResult cur = cpachecker.produceResult();
    if (t == null) {
      logger.log(Level.INFO, cur.getResultString());
      curLogger.log(Level.INFO, cur.getResultString());
    } else {
      // exception was already logged
    }

    cfaMutationManager.exportIntermediateStatistics(cpachecker.getStats(), cur);
    return new AnalysisResult(cur, t);
  }

  private static class Feasibility {
    private final boolean isFeasible;
    private final AnalysisResult foundFalse;

    private Feasibility(boolean pFeasible, AnalysisResult pFalse) {
      isFeasible = pFeasible;
      foundFalse = pFalse;
    }

    static Feasibility withRestoredCex(AnalysisResult pFalse) {
      return new Feasibility(true, pFalse);
    }

    static Feasibility withoutRestoredCex() {
      return new Feasibility(true, null);
    }

    static Feasibility noFeasibleErrorFound() {
      return new Feasibility(false, null);
    }
  }

  /** Mostly copied from {@link CounterexampleCheckAlgorithm} */
  private Feasibility isFeasible(AnalysisResult pResult) throws InvalidConfigurationException {

    CounterexampleChecker cexChecker =
        cfaMutationManager.createCexCheckerAndStartLimits(pResult, cfaMutator);
    LogManager curLogger = cfaMutationManager.getCurrentLogger();
    ReachedSet reached = pResult.result.getReached();
    assert ARGUtils.checkARG(reached);
    ARGState rootState = (ARGState) reached.getFirstState();

    final List<ARGState> errorStates =
        from(reached)
            .transform(AbstractStates.toState(ARGState.class))
            .filter(AbstractStates::isTargetState)
            .toList();

    assert !errorStates.isEmpty() : "no error states while CEX check";

    // check counterexample
    for (ARGState errorState : errorStates) {
      curLogger.log(
          Level.FINE,
          "Path to error state",
          errorState.getClass().getSimpleName(),
          errorState.getStateId(),
          errorState
              .getCounterexampleInformation()
              .map(cex -> String.format("(CEX #%d)", cex.getUniqueId()))
              .orElse("(without CEX info)"),
          "found, starting counterexample check");

      // if (ambigiousARG) {
      // statesOnErrorPath = SlicingAbstractionsUtils.getStatesOnErrorPath(errorState);
      ImmutableSet<ARGState> statesOnErrorPath = ARGUtils.getAllStatesOnPathsTo(errorState);
      curLogger.log(
          Level.FINE,
          "Paths include states ",
          MoreStrings.lazyString(
              () -> from(statesOnErrorPath).transform(ARGState::getStateId).join(Joiner.on(", "))));

      try {
        if (cexChecker.checkCounterexample(rootState, errorState, statesOnErrorPath)) {
          logger.log(Level.INFO, "Error path confirmed by counterexample check");
          if (cexChecker instanceof CexAsProgramCPAchecker) {
            return Feasibility.withRestoredCex(((CexAsProgramCPAchecker) cexChecker).getFoundFalse());
          }
          return Feasibility.withoutRestoredCex();
        }

        curLogger.log(Level.FINE, "This error path identified as infeasible.");

      } catch (CounterexampleAnalysisFailed e) {
        logger.logUserException(
            Level.WARNING, e, "Counterexample found, but feasibility could not be verified");

      } catch (InterruptedException e) {
        // this feasibility check was too long
        logger.logf(Level.INFO, "Feasibility check interrupted (%s)", e.getMessage());
      }
    }

    cfaMutationManager.cancelCheckLimits();
    logger.log(Level.INFO, "No feasible error path found");
    return Feasibility.noFeasibleErrorFound();
  }

  // keep last result and thrown error
  public static class AnalysisResult {
    private final CPAcheckerResult result;
    private final @Nullable Throwable thrown;

    public AnalysisResult(CPAcheckerResult pResult, @Nullable Throwable pThrown) {
      result = pResult;
      thrown = pThrown;
    }

    /** Map this to {@link AnalysisOutcome} wrt original result. */
    public AnalysisOutcome toAnalysisOutcome(AnalysisResult pOriginal) {
      switch (getVerdict()) {
        case TRUE:
          return AnalysisOutcome.VERDICT_TRUE;

        case FALSE:
          return pOriginal.getVerdict() == Result.FALSE && sameTargetDescription(pOriginal)
              ? AnalysisOutcome.SAME_VERDICT_FALSE
              : AnalysisOutcome.VERDICT_FALSE;

        case NOT_YET_STARTED:
        case UNKNOWN:
          if (thrown == null) {
            return AnalysisOutcome.ANOTHER_VERDICT_UNKNOWN;

          } else if (thrown instanceof InterruptedException) {
            return AnalysisOutcome.VERDICT_UNKNOWN_BECAUSE_OF_TIMEOUT;

          } else if (sameException(pOriginal)) {
            return AnalysisOutcome.FAILURE_BECAUSE_OF_SAME_EXCEPTION;

          } else {
            return AnalysisOutcome.FAILURE_BECAUSE_OF_EXCEPTION;
          }

        default:
          throw new AssertionError();
      }
    }

    private boolean sameTargetDescription(AnalysisResult pOther) {
      return pOther.result.getTargetDescription().equals(result.getTargetDescription());
    }

    private boolean sameException(AnalysisResult pOther) {
      return thrown != null
          && pOther.thrown != null
          && pOther.thrown.getClass().equals(thrown.getClass())
          && (thrown.getStackTrace().length == 0 // optimized by JVM if many exceptions
              || pOther.thrown.getStackTrace()[0].equals(thrown.getStackTrace()[0]));
    }

    public Result getVerdict() {
      return result.getResult();
    }

    public MainCPAStatistics getStats() {
      return (MainCPAStatistics) result.getStatistics();
    }

    public CFA getCfa() {
      return result.getCfa();
    }

    public ConfigurableProgramAnalysis getCpa() {
      return result.getReached().getCPA();
    }

    public Throwable getThrown() {
      return thrown;
    }

    public String getDescription() {
      if (thrown == null) {
        return result.getResultString();
      }
      return result.getResult().toString() + ". " + thrown.getMessage();
    }
  }

  public CPAcheckerResult asMutatorResult(
      @Nullable AnalysisResult pAnalysisResult,
      Result pVerdict,
      MainCFAMutationStatistics pTotalStats) {

    Preconditions.checkNotNull(pTotalStats);
    try {
      cfaMutationManager.resetExportDirectory(AnalysisRun.DEFAULT);
    } catch (InvalidConfigurationException e) {
      logger.logUserException(Level.WARNING, e, "Cannot reset output directory");
    }

    cfaMutator.collectStatistics(pTotalStats.subStats);
    MainCPAStatistics lastMainStats = pAnalysisResult.getStats();
    Statistics stats = lastMainStats;
    if (stats != null) {
      lastMainStats.getSubStatistics().add(pTotalStats);
    } else {
      stats = pTotalStats;
    }

    String desc =
        pAnalysisResult.getVerdict() == Result.FALSE
            ? pAnalysisResult.result.getTargetDescription()
            : "";

    return new CPAcheckerResult(
        pVerdict, desc, pAnalysisResult.result.getReached(), pAnalysisResult.getCfa(), stats);
  }

  class MainCFAMutationStatistics implements Statistics {
    private Statistics cfaCreatorStats = null;
    private List<Statistics> subStats = new ArrayList<>();

    final StatTimer originalTime = new StatTimer("time for original analysis run");
    final StatTimerWithMoreOutput afterMutations =
        new StatTimerWithMoreOutput("total time for analysis after mutations");
    final StatTimerWithMoreOutput afterRollbacks =
        new StatTimerWithMoreOutput("total time for analysis after rollbacks");
    final StatTimerWithMoreOutput feasibilityCheck =
        new StatTimerWithMoreOutput("total time for feasibility check after verdict FALSE");

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {

      if (cfaCreatorStats != null) {
        pOut.println("CFA creation statistics");
        pOut.println("=======================");
        StatisticsUtils.printStatistics(cfaCreatorStats, pOut, logger, pResult, pReached);
        StatisticsUtils.writeOutputFiles(cfaCreatorStats, logger, pResult, pReached);
        pOut.println();
      }

      if (originalTime.getUpdateCount() > 0) {
        pOut.println("CFA mutation statistics");
        pOut.println("=======================");
        pOut.println("Time for analysis");
        pOut.println("-----------------");
        put(pOut, 1, originalTime);
        put(pOut, 1, afterMutations);
        if (afterRollbacks.getUpdateCount() > 0) {
          put(pOut, 1, afterRollbacks);
        }
        if (feasibilityCheck.getUpdateCount() > 0) {
          put(pOut, 1, feasibilityCheck);
        }
      }

      subStats.forEach(
          s -> {
            StatisticsUtils.printStatistics(s, pOut, logger, pResult, pReached);
            StatisticsUtils.writeOutputFiles(s, logger, pResult, pReached);
          });
    }

    public void setCFACreatorStatistics(Statistics pStats) {
      cfaCreatorStats = pStats;
    }

    @Override
    public @Nullable String getName() {
      return "CFA mutation";
    }

    @Override
    public void writeOutputFiles(Result pResult, UnmodifiableReachedSet pReached) {
      subStats.forEach(s -> StatisticsUtils.writeOutputFiles(s, logger, pResult, pReached));
    }
  }

  public static AnalysisResult analysisRound(CPAchecker pCpachecker, String pProgramFileName) {
    pCpachecker.logger.logf(
        Level.INFO, "%s (%s) started", getVersion(pCpachecker.config), getJavaInformation());
    Throwable t = null;

    try {
      pCpachecker.setupMainStats();
      CFACreator cfaCreator =
          new CFACreator(pCpachecker.config, pCpachecker.logger, pCpachecker.shutdownNotifier);
      pCpachecker.parse(cfaCreator, ImmutableList.of(pProgramFileName));
      pCpachecker.getStats().setCFACreatorStatistics(cfaCreator.getStatistics());
      assert pCpachecker.getCfa() != null;

      try {
        pCpachecker.setupAnalysis();
      } finally {
        pCpachecker.stopMainStatsCreationTimer();
      }
      pCpachecker.runAnalysis();

    } catch (InvalidConfigurationException e) {
      pCpachecker.logger.logUserException(Level.SEVERE, e, "Invalid configuration");
      t = e;

    } catch (InterruptedException e) {
      // CPAchecker must exit because it was asked to
      // we return normally instead of propagating the exception
      // so we can return the partial result we have so far
      pCpachecker.logger.logUserException(Level.WARNING, e, "Analysis interrupted");
      t = e;

    } catch (CPAException e) {
      pCpachecker.logger.logUserException(Level.SEVERE, e, null);
      t = e;

    } finally {
      pCpachecker.close();
    }

    CPAcheckerResult cpaResult = pCpachecker.produceResult();
    StatisticsUtils.writeOutputFiles(
        pCpachecker.getStats(), pCpachecker.logger, cpaResult.getResult(), cpaResult.getReached());
    return new AnalysisResult(cpaResult, t);
  }
}
