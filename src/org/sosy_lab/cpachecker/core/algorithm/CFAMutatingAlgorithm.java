// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.management.JMException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.MoreStrings;
import org.sosy_lab.common.Optionals;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.configuration.TimeSpanOption;
import org.sosy_lab.common.configuration.converters.FileTypeConverter;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.mutation.AnalysisOutcome;
import org.sosy_lab.cpachecker.cfa.mutation.CFAMutationStrategy;
import org.sosy_lab.cpachecker.cfa.mutation.CFAMutator;
import org.sosy_lab.cpachecker.cfa.mutation.CFAMutatorStatistics;
import org.sosy_lab.cpachecker.core.CPAcheckerResult;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.CexAsProgramCPAchecker;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.CounterexampleCheckAlgorithm.CounterexampleCheckerType;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.CounterexampleChecker;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.DelegatingCheckerWithRestoredFunctions;
import org.sosy_lab.cpachecker.core.defaults.MultiStatistics;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ResultProviderReachedSet;
import org.sosy_lab.cpachecker.core.specification.Specification;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CounterexampleAnalysisFailed;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.resources.ProcessCpuTime;
import org.sosy_lab.cpachecker.util.resources.ProcessCpuTimeLimit;
import org.sosy_lab.cpachecker.util.resources.ResourceLimit;
import org.sosy_lab.cpachecker.util.resources.ResourceLimitChecker;
import org.sosy_lab.cpachecker.util.resources.WalltimeLimit;

/**
 * This class provides implementation of CFA mutating algorithm.
 *
 * <p>First, run analysis on the original CFA. For example, this run results in an exception, and we
 * want to get a minimal reproducing program for this bug of CPAchecker. Second, mutate the CFA and
 * run analysis again. If the exception is reproduced on the smaller CFA, continue, otherwise
 * rollback the mutation and continue. In the end, get a minimal CFA.
 *
 * <p>Other possibilities are a maximal CFA and a 'cause' in the CFA, using Delta Debugging
 * approach. Also it is possible to search for program error (verdict False) alongside simplified
 * CFAs.
 *
 * <p>See also {@link CFAMutator} and {@link CFAMutationStrategy}. Previously this was implemented
 * via CPAcheckerMutator class.
 */
@Options
public class CFAMutatingAlgorithm extends NestingAlgorithm {

  @Option(
      secure = true,
      name = "analysis.unknownAsTrue",
      description = "Do not report unknown if analysis terminated, report true (UNSOUND!).")
  private boolean unknownAsTrue = false;

  @Option(
      secure = true,
      name = "analysis.stopAfterError",
      description = "stop after the first error has been found")
  private boolean stopAfterError = true;

  @Option(
      secure = true,
      name = "analysis.counterexampleLimit",
      description = "Maximum number of counterexamples to be created.")
  private int cexLimit = 0;

  private CFAMutator cfaMutator;
  private CFAMutatorStatistics statistics;
  private AlgorithmStatus statusDoneOrUnknown;

  private final FileAndConfigManager configManager;
  private TimeManager timeManager;
  private CFA originalCfa;
  private int round = 0;
  private int rollbacksInRow = 0;

  private LogManager roundLogger;

  public CFAMutatingAlgorithm(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      Specification pSpecification,
      CFA pCfa)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, pShutdownNotifier, pSpecification);
    originalCfa = pCfa;
    configManager = new FileAndConfigManager(pConfig, pLogger);
    timeManager = new TimeManager(globalConfig, logger);
  }

  public void setCfaMutator(CFAMutator pCfaMutator) {
    Preconditions.checkState(cfaMutator == null);
    Preconditions.checkArgument(pCfaMutator != null);
    cfaMutator = pCfaMutator;
    statistics = cfaMutator.getCfaMutationStatistics();
    cfaMutator.clearCreatorStats();
    statusDoneOrUnknown =
        cfaMutator.isSearchingForError()
            ? AlgorithmStatus.UNSOUND_AND_IMPRECISE
            : AlgorithmStatus.NO_PROPERTY_CHECKED;
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReachedSet) throws CPAException {
    checkArgument(
        pReachedSet instanceof ForwardingReachedSet,
        "CFAMutatingAlgorithm needs ForwardingReachedSet");
    checkArgument(!pReachedSet.isEmpty(), "CFAMutatingAlgorithm needs non-empty reached set");
    checkArgument(
        pReachedSet.size() <= 1,
        "CFAMutatingAlgorithm does not support being called several times with the same reached set");

    ForwardingReachedSet forwardingReachedSet = (ForwardingReachedSet) pReachedSet;
    AggregatedReachedSets aggregatedReachedSet = AggregatedReachedSets.singleton(pReachedSet);

    logger.log(Level.INFO, cfaMutator.getApproachName());

    try {
      // run analysis of the original cfa
      statistics.startOriginalTime();
      configManager.resetExportDirectoryToOriginalRound();
      runDelegateAnalysis(forwardingReachedSet, aggregatedReachedSet, originalCfa);
      originalCfa = null; // no need to store it anymore
    } catch (InterruptedException e) {
      // originalResult == null
      // not enough time even for one run
      return statusDoneOrUnknown;
    } finally {
      statistics.stopOriginalTime();
    }

    try {
      // if should return the result got
      if (cfaMutator.shouldReturnOriginalResultAlsoBuildStrategy(AnalysisResult.original.getOutcome())) {
        configManager.resetExportDirectoryToDefault();
        return AnalysisResult.original.getStatus();
      }

    } catch (InvalidConfigurationException e) {
      logger.logUserException(Level.SEVERE, e, "Cannot build CFA mutation strategy");
      configManager.resetExportDirectoryToDefault();
      return AnalysisResult.original.getStatus();
    }

    if (shutdownNotifier.shouldShutdown()) {
      configManager.resetExportDirectoryToDefault();
      return AnalysisResult.original.getStatus();
    }

    // CFAMutator stores needed info from #parse,
    // so no need to pass CFA as argument in next calls

    if (!cfaMutator.canMutate()) {
      logger.log(Level.INFO, "There are no possible mutations for the CFA of the given program.");
      configManager.resetExportDirectoryToDefault();
      return AnalysisResult.original.getStatus();
    }

    timeManager.setOriginalTime(statistics.getOriginalTime());

    try {
      while (cfaMutator.canMutate()) {
        Optional<AlgorithmStatus> roundStatus =
            mutateAnalyzeCheckRollback(forwardingReachedSet, aggregatedReachedSet);
        if (roundStatus.isPresent()) {
          return roundStatus.get();
        }
      }

    } catch (InterruptedException e) {
      return statusDoneOrUnknown;

    } catch (InvalidConfigurationException | ParserException e) {
      logger.logUserException(Level.SEVERE, e, "Cannot mutate or rollback CFA");
      throw new CPAException(e.getMessage());
    }

    logger.log(Level.INFO, "CFA mutation ended, as all possible mutations were tried.");
    return statusDoneOrUnknown;
  }

  private Optional<AlgorithmStatus> mutateAnalyzeCheckRollback(
      ForwardingReachedSet pForwardingReachedSet, AggregatedReachedSets pAggregatedReachedSet)
      throws InterruptedException, InvalidConfigurationException, ParserException {

    shutdownNotifier.shutdownIfNecessary();

    round++;
    logger.log(Level.INFO, "Mutation round", round);
    configManager.resetExportDirectoryToMutationRound(round);

    CFA cfa = cfaMutator.mutate();
    AnalysisResult delegateResult = null;

    try {
      // run analysis on a mutated cfa
      statistics.startTimeAfterMutations();
      delegateResult = runDelegateAnalysis(pForwardingReachedSet, pAggregatedReachedSet, cfa);
      // TODO export intermediate results
      // XXX it is incorrect to save intermediate stats, as reached is updated?
    } finally {
      timeManager.cancelLimits();
      statistics.stopTimeAfterMutations();
    }

    Optional<AlgorithmStatus> feasibilityResult =
        checkFeasibilityIfNeeded(delegateResult.getCpa(), pForwardingReachedSet, cfa);
    if (feasibilityResult.isPresent()) {
      return feasibilityResult;
    } // else just continue

    rollbackIfNeeded(pForwardingReachedSet, pAggregatedReachedSet, delegateResult.getOutcome());
    return Optional.empty();
  }

  /** Check feasibility of found CEX against original program, if we are searching for errors. */
  // TODO replace with mutCEX -> automaton -> spec -> analysis via algorithm
  // instead of running {@link CexAsProgramCPAchecker}
  private Optional<AlgorithmStatus> checkFeasibilityIfNeeded(
      ConfigurableProgramAnalysis pCpa, ForwardingReachedSet pForwardingReachedSet, CFA pCfa)
      throws InterruptedException {
    if (!pForwardingReachedSet.wasTargetReached() || !cfaMutator.isSearchingForError()) {
      // no false -- nothing to check
      // or not caring for false
      return Optional.empty();
    }

    shutdownNotifier.shutdownIfNecessary();
    statistics.startFeasilityCheck();

    try {
      // check mutated CEX against original CFA
      logger.log(Level.INFO, "Found a counterexample. Checking it on original program.");
      configManager.resetExportDirectoryToFeasibilityCheckAfterRound(round);
      DelegatingCheckerWithRestoredFunctions cexChecker;

      try {
        cexChecker = createCexCheckerAndStartLimits(pCpa, pCfa);
      } catch (InvalidConfigurationException e) {
        roundLogger.logException(Level.WARNING, e, "Cannot create CEX checker");
        logger.logUserException(Level.WARNING, e, "Cannot check feasibility");
        return Optional.empty();
      }

      assert ARGUtils.checkARG(pForwardingReachedSet) : "incorrect ARG"; // XXX
      ARGState rootState = (ARGState) pForwardingReachedSet.getFirstState();

      final List<ARGState> errorStates =
          from(pForwardingReachedSet)
              .transform(AbstractStates.toState(ARGState.class))
              .filter(AbstractStates::isTargetState)
              .toList();

      assert !errorStates.isEmpty() : "no error states while CEX check";

      boolean isFeasible = false;
      for (ARGState errorState : errorStates) {
        try {
          isFeasible = checkFeasibilityOfErrorState(cexChecker, rootState, errorState);
        } catch (InterruptedException e) {
          assert shutdownNotifier.shouldShutdown()
              : "Feasibility check interrupted, expected global timeout";
          throw e;
        }

        if (isFeasible) {
          statistics.stopFeasibilityCheck();
          if (cexChecker.getDelegate() instanceof CexAsProgramCPAchecker) {
            CPAcheckerResult cexCheckResult =
                ((CexAsProgramCPAchecker) cexChecker.getDelegate()).getResult();
            pForwardingReachedSet.setDelegate(cexCheckResult.getReached());
            Statistics mainCpaStatisticsOfFeasibilityCheck = cexCheckResult.getStatistics();
            statistics.setLastRoundStatistics(mainCpaStatisticsOfFeasibilityCheck, false);
          }
          return Optional.of(AlgorithmStatus.UNSOUND_AND_PRECISE);
        }
      }

      logger.log(Level.INFO, "No feasible error path found");
      return Optional.empty();

    } finally {
      timeManager.cancelLimits();
      statistics.stopTimeAfterRollback();
    }
  }

  private DelegatingCheckerWithRestoredFunctions createCexCheckerAndStartLimits(
      ConfigurableProgramAnalysis pCpa, CFA pCfa) throws InvalidConfigurationException {

    Configuration roundConfig =
        configManager.createRoundConfig(round, timeManager.createFeasibilityCheckOptions());
    roundLogger = BasicLogManager.create(roundConfig);
    ShutdownManager fMan = ShutdownManager.createWithParent(shutdownNotifier);
    timeManager.startCheckLimits(roundLogger, fMan);

    ARGCPA argCpa = CPAs.retrieveCPA(pCpa, ARGCPA.class);
    if (argCpa == null) {
      throw new InvalidConfigurationException(
          "Counterexample check after CFA mutation expected ARG CPA, but got "
              + CPAs.asIterable(pCpa).transform(subcpa -> subcpa.getClass().getSimpleName()));
    }

    return new DelegatingCheckerWithRestoredFunctions(
        roundConfig, roundLogger, pCfa, fMan, timeManager.getCheckerType(), cfaMutator);
  }

  private boolean checkFeasibilityOfErrorState(
      CounterexampleChecker pCexChecker, ARGState pRootState, ARGState pErrorState)
      throws InterruptedException {

    shutdownNotifier.shutdownIfNecessary();
    roundLogger.log(
        Level.FINE,
        "Path to error state",
        pErrorState.getClass().getSimpleName(),
        pErrorState.getStateId(),
        pErrorState
            .getCounterexampleInformation()
            .map(cex -> String.format("(CEX #%d)", cex.getUniqueId()))
            .orElse("(without CEX info)"),
        "found, starting counterexample check");

    // if (ambigiousARG) {
    // statesOnErrorPath = SlicingAbstractionsUtils.getStatesOnErrorPath(errorState);
    ImmutableSet<ARGState> statesOnErrorPath = ARGUtils.getAllStatesOnPathsTo(pErrorState);
    roundLogger.log(
        Level.FINE,
        "Paths include states ",
        MoreStrings.lazyString(
            () -> from(statesOnErrorPath).transform(ARGState::getStateId).join(Joiner.on(", "))));

    try {
      if (pCexChecker.checkCounterexample(pRootState, pErrorState, statesOnErrorPath)) {
        logger.log(Level.INFO, "Error path confirmed by counterexample check");
        roundLogger.log(Level.INFO, "Error path confirmed by counterexample check");
        return true;
      }
      roundLogger.log(Level.FINE, "This error path identified as infeasible.");

    } catch (CounterexampleAnalysisFailed e) {
      logger.logUserException(
          Level.WARNING, e, "Counterexample found, but feasibility could not be verified");
      roundLogger.logException(
          Level.WARNING, e, "Counterexample found, but feasibility could not be verified");

    } catch (InterruptedException e) {
      if (shutdownNotifier.shouldShutdown()) {
        throw e;
      }
      // this feasibility check was too long
      logger.log(Level.INFO, "Feasibility check interrupted:", e.getMessage());
      roundLogger.logException(Level.WARNING, e, "Feasibility check interrupted");
    }

    return false;
  }

  private void rollbackIfNeeded(
      ForwardingReachedSet forwardingReachedSet,
      AggregatedReachedSets aggregatedReachedSets,
      AnalysisOutcome pOutcome)
      throws InterruptedException, InvalidConfigurationException, ParserException {

    shutdownNotifier.shutdownIfNecessary();
    configManager.resetExportDirectoryToRollbackAfterRound(round);

    CFA cfa = cfaMutator.setResult(pOutcome);

    if (cfa == null
        || configManager.getCheckAfterRollbacks() == 0
        || ++rollbacksInRow % configManager.getCheckAfterRollbacks() != 0) {
      return;
    }

    try {
      statistics.stopTimeAfterRollback();
      logger.log(Level.INFO, "Running analysis after", rollbacksInRow, "mutation rollback in row");
      // Check that property is still preserved after rollback
      AnalysisResult delegateResult =
          runDelegateAnalysis(forwardingReachedSet, aggregatedReachedSets, cfa);
      // If analysis ended because of a global shutdown, the result may be TIMEOUT
      // otherwise, check it is expected one
      if (!shutdownNotifier.shouldShutdown()
          || delegateResult.getOutcome() != AnalysisOutcome.VERDICT_UNKNOWN_BECAUSE_OF_TIMEOUT) {
        cfaMutator.verifyOutcome(delegateResult.getOutcome());
      }

    } finally {
      timeManager.cancelLimits();
      statistics.stopTimeAfterRollback();
    }
  }

  /**
   * Run original analysis on original or mutated CFA.
   *
   * @throws InterruptedException only if global time exceeded. Timeout for current round is
   *     returned as a corresponding {@linkplain AnalysisResult}.
   */
  // we catch CPAchecker bugs
  @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
  private AnalysisResult runDelegateAnalysis(
      ForwardingReachedSet pForwardingreachedSet,
      AggregatedReachedSets pAggregatedReachedSets,
      CFA pCfa)
      throws InterruptedException {

    ConfigurableProgramAnalysis cpa = null;
    Algorithm algorithm = null;
    ReachedSet reached = null;
    AlgorithmStatus status = AlgorithmStatus.SOUND_AND_PRECISE;
    Throwable t = null;
    shutdownNotifier.shutdownIfNecessary();

    try {
      Configuration roundConfig =
          configManager.createRoundConfig(round, timeManager.getRoundLimitOptionsForAnalysis());
      roundLogger = BasicLogManager.create(roundConfig);

      ShutdownManager roundShutdownManager = ShutdownManager.createWithParent(shutdownNotifier);
      timeManager.startAnalysisLimits(roundShutdownManager, roundLogger);

      CoreComponentsFactory factory =
          new CoreComponentsFactory(
              roundConfig, roundLogger, roundShutdownManager.getNotifier(), pAggregatedReachedSets);

      Triple<ConfigurableProgramAnalysis, Algorithm, ReachedSet> triple =
          createAlgorithmCpaAndReachedAndStartLimits(factory, pCfa);
      cpa = triple.getFirst();
      algorithm = triple.getSecond();
      reached = triple.getThird();

      roundLogger.log(Level.INFO, "Starting analysis ...");
      int counterExampleCount = 0;

      do {
        status = status.update(algorithm.run(reached));

        if (cexLimit > 0) {
          counterExampleCount =
              Optionals.presentInstances(
                      from(reached)
                          .filter(AbstractStates::isTargetState)
                          .filter(ARGState.class)
                          .transform(ARGState::getCounterexampleInformation))
                  .size();
        }
        // // copied from {@link CPAchecker#runAnalysis}
        // // running and finding as much errors as possible is beneficial XXX
        // either run only once (if stopAfterError == true)
        // or until the waitlist is empty
        // or until maximum number of counterexamples is reached
      } while (!stopAfterError
          && reached.hasWaitingState()
          && (cexLimit == 0 || cexLimit > counterExampleCount));

      roundLogger.log(Level.INFO, "Stopping analysis ...");

    } catch (InvalidConfigurationException e) {
      t = e;
      logger.log(Level.INFO, "Cannot create delegate algorithm:", e.getMessage());
      roundLogger.logException(Level.WARNING, e, "Cannot create delegate algorithm");

    } catch (InterruptedException e) {
      if (shutdownNotifier.shouldShutdown()) {
        pForwardingreachedSet.setDelegate(reached);
        throw e;
      }

      // this round was too long
      t = e;
      logger.log(Level.INFO, "Timeout:", e.getMessage());
      roundLogger.logException(Level.WARNING, e, "Analysis round interrupted");

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
        roundLogger.logf(Level.WARNING, "Recurring %s (no stack trace)", e.getClass());
      } else {
        // so benchexec wont say EXCEPTION in the results table just because an exception occured
        String exceptionClassName = e.getClass().getSimpleName();
        exceptionClassName = exceptionClassName.replace("Exception", " exception");
        exceptionClassName = exceptionClassName.replace("Error", " error");

        if (e.getMessage() == null) {
          logger.log(Level.INFO, exceptionClassName, "(no message)");
        } else {
          logger.logf(Level.INFO, "%s: %s", exceptionClassName, e.getMessage());
        }
        roundLogger.logException(Level.WARNING, e, null);
      }
    } finally {
      timeManager.cancelLimits();
    }

    pForwardingreachedSet.setDelegate(reached);
    return new AnalysisResult(status, t, reached, unknownAsTrue, cpa);
  }

  private Triple<ConfigurableProgramAnalysis, Algorithm, ReachedSet>
      createAlgorithmCpaAndReachedAndStartLimits(CoreComponentsFactory pFactory, CFA pCfa)
          throws InvalidConfigurationException, CPAException, InterruptedException {

    ConfigurableProgramAnalysis cpa = pFactory.createCPA(pCfa, specification);
    GlobalInfo.getInstance().setUpInfoFromCPA(cpa);
    Algorithm algorithm = pFactory.createAlgorithm(cpa, pCfa, specification);

    roundLogger.log(Level.FINE, "Creating initial reached set");

    AbstractState initialState =
        cpa.getInitialState(pCfa.getMainFunction(), StateSpacePartition.getDefaultPartition());
    Precision initialPrecision =
        cpa.getInitialPrecision(pCfa.getMainFunction(), StateSpacePartition.getDefaultPartition());

    ReachedSet reached = pFactory.createReachedSet(cpa);
    reached.add(initialState, initialPrecision);

    // stash statistics of current run, then collect it if it was last run
    MultiStatistics stats =
        new MultiStatistics(logger) {
          @Override
          public @Nullable String getName() {
            return null; // header is added in CFAMutatorStatistics
          }
        };

    if (cpa instanceof StatisticsProvider) {
      ((StatisticsProvider) cpa).collectStatistics(stats.getSubStatistics());
    }
    if (algorithm instanceof StatisticsProvider) {
      ((StatisticsProvider) algorithm).collectStatistics(stats.getSubStatistics());
    }

    statistics.setLastRoundStatistics(stats, round == 0);

    return Triple.of(cpa, algorithm, reached);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
  }

  @Options(prefix = "cfaMutation")
  static final class TimeManager {

    @Option(
        secure = true,
        name = "timelimit.factor",
        description =
            "Sometimes analysis run can be unpredictably long. To run many runs successfully, "
                + "CFA mutator hard-caps time for every run (200s by default), and also soft-caps "
                + "time for the runs after original by multiplying used time by the factor and "
                + "adding the bias. By default soft cap is orig.time * 2.0 + 5s. Original (first) "
                + "run gets hard-cap time limit, all others get the lower between the two.\n"
                + "Wall and (if possible) CPU time limits will be produced with same time span.")
    private double timelimitFactor = 2.0;

    @TimeSpanOption(codeUnit = TimeUnit.SECONDS, defaultUserUnit = TimeUnit.SECONDS, min = 0)
    @Option(
        secure = true,
        name = "timelimit.add",
        description =
            "Sometimes analysis run can be unpredictably long. To run many runs successfully, "
                + "CFA mutator hard-caps time for every run (200s by default), and also soft-caps "
                + "time for the runs after original by multiplying used time by the factor and "
                + "adding the bias. By default soft cap is orig.time * 2.0 + 5s. Original (first) "
                + "run gets hard-cap time limit, all others get the lower between the two.\n"
                + "Wall and (if possible) CPU time limits will be produced with same time span.")
    private TimeSpan timelimitBias = TimeSpan.ofSeconds(5);

    @TimeSpanOption(codeUnit = TimeUnit.SECONDS, defaultUserUnit = TimeUnit.SECONDS, min = 10)
    @Option(
        secure = true,
        name = "timelimit.hardcap",
        description =
            "Sometimes analysis run can be unpredictably long. To run many runs successfully, "
                + "CFA mutator hard-caps time for every run (200s by default), and also soft-caps "
                + "time for the runs after original by multiplying used time by the factor and "
                + "adding the bias. By default soft cap is orig.time * 2.0 + 5s. Original (first) "
                + "run gets hard-cap time limit, all others get the lower between the two.\n"
                + "Wall and (if possible) CPU time limits will be produced with same time span.")
    private TimeSpan hardcap = TimeSpan.ofSeconds(200);

    @TimeSpanOption(codeUnit = TimeUnit.SECONDS, defaultUserUnit = TimeUnit.SECONDS, min = 10)
    @Option(
        secure = true,
        name = "cex.timelimit",
        description =
            "Limit time for countrexample feasibility check. This option is used only if CFA "
                + "mutations are used to find a feasible error. As with analysis timelimit, "
                + "wall and (if possible) CPU time limits will be produced with same time span.")
    private TimeSpan timeForCex = TimeSpan.ofSeconds(100);

    @Option(
        secure = true,
        name = "cex.checker",
        description =
            "Which model checker to use for verifying counterexample when an error is found using "
                + "CFA mutations. Currently CBMC, CPAchecker with same or different config, or the "
                + "concrete execution checker can be used.")
    private CounterexampleCheckerType checkerType = CounterexampleCheckerType.CPACHECKER;

    @Option(
        secure = true,
        name = "cex.checker.config",
        description =
            "If CPAchecker is used as CEX-checker, it uses this configuration file or "
                + "defaults to same configuration that found CEX.")
    private @Nullable String cpacheckerConfigFile;

    private final LogManager globalLogger;

    private TimeSpan originalRun;
    private TimeSpan lowercap;

    // XXX {@link RestartingAlgorithm} forgets running limits, is it ok?
    // i need this field to be able to cancel them
    private ResourceLimitChecker roundLimits;
    private boolean roundLimitsWereStarted = false;

    private final boolean useCpuTimeLimit;

    TimeManager(Configuration pConfig, LogManager pLogger) throws InvalidConfigurationException {
      globalLogger = pLogger;

      pConfig.inject(this, TimeManager.class);
      lowercap = hardcap;

      Throwable t = null;
      try {
        ProcessCpuTime.read();
      } catch (JMException e) {
        t = e;
        globalLogger.log(Level.INFO, "Cannot use CPU time limits.");
      }
      /*
       * Google App Engine does not allow to use classes from the package java.lang.management.
       * Therefore it throws a NoClassDefFoundError if this is attempted regardless. To prevent
       * CPAchecker from crashing in this case we catch the error and log the event.
       */
      catch (NoClassDefFoundError e) {
        t = e;
        globalLogger.log(Level.INFO, "Cannot use CPU time limits in Google App Engine.");
      }

      useCpuTimeLimit = t == null;
    }

    CounterexampleCheckerType getCheckerType() {
      return checkerType;
    }

    void setOriginalTime(TimeSpan pConsumedTime) {
      Preconditions.checkState(originalRun == null);
      originalRun = pConsumedTime;

      double softcap = originalRun.asMillis() * timelimitFactor + timelimitBias.asMillis();
      if (softcap < hardcap.asMillis()) {
        lowercap = TimeSpan.ofMillis((long) softcap);
      }

      globalLogger.log(
          Level.INFO,
          "Using",
          Joiner.on(", ")
              .join(Iterables.transform(produceLimitsFromNowOn(lowercap), ResourceLimit::getName)),
          "for the following rounds");
    }

    private ImmutableList<ResourceLimit> produceLimitsFromNowOn(TimeSpan pTime) {
      ImmutableList.Builder<ResourceLimit> result = ImmutableList.builder();
      result.add(WalltimeLimit.fromNowOn(pTime));
      if (useCpuTimeLimit) {
        try {
          result.add(ProcessCpuTimeLimit.fromNowOn(pTime));
        } catch (JMException e) {
          throw new AssertionError(e);
        }
      }

      return result.build();
    }

    Map<String, String> getRoundLimitOptionsForAnalysis() {
      ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
      result.put("limits.time.wall", lowercap.toString());
      if (useCpuTimeLimit) {
        result.put("limits.time.cpu", lowercap.toString());
      }
      return result.buildOrThrow();
    }

    Map<String, String> createFeasibilityCheckOptions() throws InvalidConfigurationException {
      ImmutableMap.Builder<String, String> result = ImmutableMap.builder();

      result.put("counterexample.checker", checkerType.name());

      switch (checkerType) {
        case CBMC:
          result.put("cbmc.timelimit", timeForCex.toString());
          break;

        case CONCRETE_EXECUTION:
          result.put("counterexample.concrete.timelimit", timeForCex.toString());
          break;

        case CPACHECKER:
          if (!Strings.isNullOrEmpty(cpacheckerConfigFile)) {
            try {
              NestingAlgorithm.configToMap(
                  Configuration.builder().loadFromFile(cpacheckerConfigFile).build());
            } catch (IOException e) {
              throw new InvalidConfigurationException(
                  "Cannot read configuraton for counterexmple check from file: " + e.getMessage(),
                  e);
            }
          }

          // other checkers use only one time limit (XXX cpu or wall)
          result.put("limits.time.wall", timeForCex.toString());
          if (useCpuTimeLimit) {
            result.put("limits.time.cpu", timeForCex.toString());
          }
          break;

        default:
          throw new AssertionError("unexpected CEX checker type " + checkerType);
      }

      return result.buildOrThrow();
    }

    void startAnalysisLimits(ShutdownManager pShutdownManager, LogManager pLogger) {
      ImmutableList<ResourceLimit> timeSpans = produceLimitsFromNowOn(lowercap);
      roundLimits = new ResourceLimitChecker(pShutdownManager, timeSpans);

      if (timeSpans.isEmpty()) {
        pLogger.log(Level.INFO, "No resource limits for analysis round specified");

      } else {
        pLogger.log(
            Level.INFO,
            "Using",
            Joiner.on(", ").join(Iterables.transform(timeSpans, ResourceLimit::getName)),
            "for analysis round");
      }

      roundLimitsWereStarted = true;
      roundLimits.start();
    }

    void startCheckLimits(LogManager pLogger, ShutdownManager pShutdownManager) {
      ImmutableList<ResourceLimit> timeSpans = produceLimitsFromNowOn(timeForCex);
      roundLimits = new ResourceLimitChecker(pShutdownManager, timeSpans);

      if (timeSpans.isEmpty()) {
        pLogger.log(Level.INFO, "No resource limits for analysis round specified");

      } else {
        pLogger.log(
            Level.INFO,
            "Using",
            Joiner.on(", ").join(Iterables.transform(timeSpans, ResourceLimit::getName)),
            "for analysis round");
      }

      roundLimitsWereStarted = true;
      roundLimits.start();
    }

    void cancelLimits() {
      if (roundLimitsWereStarted) {
        roundLimitsWereStarted = false;
        roundLimits.cancel();
      }
    }
  }

  @Options(prefix = "cfaMutation")
  static final class FileAndConfigManager {

    @Option(
        secure = true,
        name = "log.consoleLevel",
        description = "Log to console every round with this logging level.")
    private Level logConsoleLevel = Level.OFF;

    @Option(
        secure = true,
        name = "logFile.level",
        description =
            "Create log file for every round with this logging level.\n"
                + "Use main file logging level if that is lower.")
    private Level logFileLevel = Level.FINE;

    @Option(
        secure = true,
        description =
            "Create log file for every round with this logging level.\n"
                + "Use main file logging level if that is lower.")
    @FileOption(Type.OUTPUT_FILE)
    private Path logFile = Path.of("this-round.log");

    @Option(secure = true, description = "File to dump statistics of current round to")
    @FileOption(Type.OUTPUT_FILE)
    private Path statFile = Path.of("this-round-stats.txt");

    @Option(
        secure = true,
        description =
            "File to write how frequent CFA node is among resulted reached set states' locations.")
    @FileOption(Type.OUTPUT_FILE)
    private Path rankedNodesFile = Path.of("this-round-ranked-nodes.txt");

    @Option(
        secure = true,
        description = "Whether to export each round in different subdirectories.")
    private boolean writeRoundsToDifferentDirectories = false;

    @Option(
        secure = true,
        name = "rollbacksInRowCheck",
        description =
            "If a mutation round is unsuccessfull (i.e. sought-for bug does not occur), "
                + "the mutation is rollbacked. If this count of rollbacks occur in row, "
                + "check that rollbacked CFA produces the sought-for bug.\n"
                + "If set to 0, do not check any rollbacks.\n"
                + "If set to 1, check that bug occurs after every rollback.\n"
                + "If set to 2, check every other one that occurs immediately after "
                + "another one, so if 5 rollbacks occur in a row, 2nd and 4th "
                + "will be checked. And so on.")
    private int checkAfterRollbacks = 5;

    private final Configuration globalConfig;
    private final LogManager globalLogger;
    private final Path defaultOutputDirectory;
    private Path currentOutputDirectory;
    private final boolean disableOutput;

    @SuppressWarnings("deprecation")
    FileAndConfigManager(Configuration pConfig, LogManager pLogger)
        throws InvalidConfigurationException {
      globalConfig = pConfig;
      globalLogger = pLogger;
      globalConfig.inject(this, FileAndConfigManager.class);

      FileTypeConverter ftc =
          ((FileTypeConverter) Configuration.getDefaultConverters().get(FileOption.class));
      currentOutputDirectory = defaultOutputDirectory = ftc.getOutputPath();
      disableOutput = "true".equals(globalConfig.getProperty("output.disable")); // XXX better
      // undo resolving files from default directory, but keep whether they are null
      logFile = logFile == null ? null : defaultOutputDirectory.relativize(logFile);
      statFile = statFile == null ? null : defaultOutputDirectory.relativize(statFile);
      rankedNodesFile =
          rankedNodesFile == null ? null : defaultOutputDirectory.relativize(rankedNodesFile);
    }

    int getCheckAfterRollbacks() {
      return checkAfterRollbacks;
    }

    void resetExportDirectoryToDefault() {
      resetExportDirectory("");
    }

    void resetExportDirectoryToOriginalRound() {
      resetExportDirectory("0-original-round");
    }

    void resetExportDirectoryToMutationRound(int round) {
      resetExportDirectory(String.valueOf(round) + "-mutation-round");
    }

    void resetExportDirectoryToRollbackAfterRound(int round) {
      resetExportDirectory(String.valueOf(round) + "-mutation-round-rollback");
    }

    void resetExportDirectoryToFeasibilityCheckAfterRound(int round) {
      resetExportDirectory(String.valueOf(round) + "-mutation-round-feasibility");
    }

    void resetExportDirectory(String pResolution) {
      if (!writeRoundsToDifferentDirectories && !pResolution.isEmpty()) {
        return;
      }
      currentOutputDirectory = defaultOutputDirectory.resolve(pResolution);
      try {
        FileTypeConverter fileTypeConverter =
            FileTypeConverter.create(
                Configuration.builder()
                    .setOption("output.path", currentOutputDirectory.toString())
                    .setOption("output.disable", Boolean.toString(disableOutput))
                    .build());
        Configuration.getDefaultConverters().put(FileOption.class, fileTypeConverter);
      } catch (InvalidConfigurationException e) {
        globalLogger.logUserException(Level.WARNING, e, "Cannot reset output directory:");
        writeRoundsToDifferentDirectories = false;
      }
    }

    Configuration createRoundConfig(int pRound, Map<String, String> pLimits)
        throws InvalidConfigurationException {

      ConfigurationBuilder builder = Configuration.builder().copyFrom(globalConfig);

      Map<String, String> configAsMap = NestingAlgorithm.configToMap(globalConfig);
      for (String key : configAsMap.keySet()) {
        if (key.startsWith("cfaMutation")) {
          builder.clearOption(key);
        }
      }

      builder.setOption("log.consoleLevel", logConsoleLevel.toString());
      // builder.setOption("log.consoleExclude", "");
      builder.setOption("log.level", logFileLevel.toString());
      // builder.setOption("log.fileExclude", "");

      if (logFile != null) {
        builder.setOption("log.file", logFile.toString());
      } else {
        builder.clearOption("log.file");
      }

      builder.setOptions(pLimits);

      builder.addConverter(
          FileOption.class, Configuration.getDefaultConverters().get(FileOption.class));
      Configuration result = builder.build();
      NestingAlgorithm.checkConfigs( // yes, path here is used just as a string actually
          globalConfig,
          result,
          Path.of(String.format("Config for %d. round", pRound)),
          globalLogger);
      return result;
    }
  }

  // keep last result and thrown error
  static final class AnalysisResult {
    private static AnalysisResult original;

    private final AlgorithmStatus status;
    private final @Nullable Throwable thrown;
    private final CPAcheckerResult.Result verdict;
    private final String targetDescription;
    private final AnalysisOutcome outcome;

    private final ConfigurableProgramAnalysis cpa;

    public AnalysisResult(
        AlgorithmStatus pStatus,
        @Nullable Throwable pThrown,
        ReachedSet pReached,
        boolean pUnknownAsTrue,
        ConfigurableProgramAnalysis pCpa) {

      cpa = pCpa;

      status = pStatus;
      thrown = pThrown;

      assert pStatus.wasPropertyChecked() : "It is expected that property was checked";

      if (original == null) {
        original = this;
      }

      targetDescription = Joiner.on(", ").join(pReached.getTargetInformation());

      if (thrown != null) {
        verdict = Result.UNKNOWN;
        if (thrown instanceof InterruptedException) {
          // XXX if (pReached.wasTargetReached()) { verdict = Result.FALSE; outcome = FALSE; }
          outcome = AnalysisOutcome.VERDICT_UNKNOWN_BECAUSE_OF_TIMEOUT;
        } else if (sameExceptionAsOriginal(pThrown)) {
          outcome = AnalysisOutcome.FAILURE_BECAUSE_OF_SAME_EXCEPTION;
        } else {
          outcome = AnalysisOutcome.FAILURE_BECAUSE_OF_EXCEPTION;
        }
        return;
      }

      if (pReached.wasTargetReached()) {
        verdict = status.isPrecise() ? Result.FALSE : Result.UNKNOWN;
      } else if (pReached instanceof ResultProviderReachedSet) {
        verdict = ((ResultProviderReachedSet) pReached).getOverallResult();
      } else if (pReached.hasWaitingState() || !status.isSound()) {
        verdict = pUnknownAsTrue ? Result.TRUE : Result.UNKNOWN;
      } else {
        verdict = Result.TRUE;
      }

      if (verdict == Result.TRUE) {
        outcome = AnalysisOutcome.VERDICT_TRUE;
      } else if (verdict == Result.FALSE) {
        outcome =
            original.verdict == Result.FALSE && original.targetDescription.equals(targetDescription)
                ? AnalysisOutcome.SAME_VERDICT_FALSE
                : AnalysisOutcome.VERDICT_FALSE;
      } else {
        // assert thrown == null;
        outcome = AnalysisOutcome.ANOTHER_VERDICT_UNKNOWN;
      }
    }

    private static boolean sameExceptionAsOriginal(Throwable pThrown) {
      return pThrown != null
          && original.thrown != null
          && original.thrown.getClass().equals(pThrown.getClass())
          && (pThrown.getStackTrace().length == 0 // optimized by JVM if many exceptions
              || original.thrown.getStackTrace()[0].equals(pThrown.getStackTrace()[0]));
    }

    public AlgorithmStatus getStatus() {
      return status;
    }

    public AnalysisOutcome getOutcome() {
      return outcome;
    }

    public ConfigurableProgramAnalysis getCpa() {
      return cpa;
    }
  }
}
