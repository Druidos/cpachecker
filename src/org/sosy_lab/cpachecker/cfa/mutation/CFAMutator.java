// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.mutation;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.ClassOption;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.configuration.TimeSpanOption;
import org.sosy_lab.common.configuration.converters.FileTypeConverter;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.export.DOTBuilder2;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.mutation.CFAMutationStrategy.MutationRollback;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.Pair;

/**
 * Mutates the CFA before next analysis run, mainly to minimize and simplify CFA. Operates on {@link
 * FunctionCFAsWithMetadata}. All processings in {@link CFACreator#createCFA} are applied after this
 * to get proper CFA for analysis run.
 */
@Options(prefix = "cfaMutation")
public class CFAMutator extends CFACreator {

  private static enum CFAMutationBehavior {
    RETURN_ORIGINAL,
    MINIMIZE,
    MAXIMIZE,
    ISOLATE_DIFFERENCE,
    SEARCH_FOR_ERROR_MIN,
    SEARCH_FOR_ERROR_MAX;

    @Override
    public String toString() {
      switch (this) {
        case MAXIMIZE:
          return "maximize CFA";
        case MINIMIZE:
          return "minimize CFA";
        case ISOLATE_DIFFERENCE:
          return "isolate a difference in CFA";
        case RETURN_ORIGINAL:
          return "return original result";
        case SEARCH_FOR_ERROR_MAX:
          return "search for feasible error along maxes";
        case SEARCH_FOR_ERROR_MIN:
          return "search for feasible error along mins";
        default:
          throw new AssertionError();
      }
    }
  }

  @Option(
      secure = true,
      description =
          "What to do if original result is TRUE, FALSE, an exception, timeout, UNKNOWN.\n"
              + "RETURN_ORIGINAL: just return original result;\n"
              + "MINIMIZE: find minimal CFA with same 'bad' result\n"
              + "    1. same exception by default has to be of the same class and with same\n"
              + "    first stack element;"
              + "    2. same FALSE by default has to have same target description;\n"
              + "    3. you can override outcomes allowed for the minimum in\n"
              + "    'cfaMutation.dd.minProperty';\n"
              + "MAXIMIZE: find maximal CFA with 'good' result (TRUE or FALSE by default;\n"
              + "    you can override outcomes allowed for the maximum in\n"
              + "    'cfaMutation.dd.maxProperty';\n"
              + "ISOLATE_DIFFERENCE: find a minimal difference in CFA, that causes 'bad' result\n"
              + "    when present, and 'good' result is produced with it absent;\n"
              + "SEARCH_FOR_ERROR: repeatedly find new min or max untill an error (FALSE) that is"
              + "    feasible on original program is found.")
  private CFAMutationBehavior ifTrue = CFAMutationBehavior.RETURN_ORIGINAL;

  @Option(
      secure = true,
      description =
          "What to do if original result is TRUE, FALSE, an exception, timeout, UNKNOWN.\n"
              + "RETURN_ORIGINAL: just return original result;\n"
              + "MINIMIZE: find minimal CFA with same 'bad' result\n"
              + "    1. same exception by default has to be of the same class and with same\n"
              + "    first stack element;"
              + "    2. same FALSE by default has to have same target description;\n"
              + "    3. you can override outcomes allowed for the minimum in\n"
              + "    'cfaMutation.dd.minProperty';\n"
              + "MAXIMIZE: find maximal CFA with 'good' result (TRUE or FALSE by default;\n"
              + "    you can override outcomes allowed for the maximum in\n"
              + "    'cfaMutation.dd.maxProperty';\n"
              + "ISOLATE_DIFFERENCE: find a minimal difference in CFA, that causes 'bad' result\n"
              + "    when present, and 'good' result is produced with it absent;\n"
              + "SEARCH_FOR_ERROR: repeatedly find new min or max untill an error (FALSE) that is"
              + "    feasible on original program is found.")
  private CFAMutationBehavior ifFalse = CFAMutationBehavior.RETURN_ORIGINAL;

  @Option(
      secure = true,
      description =
          "What to do if original result is TRUE, FALSE, an exception, timeout, UNKNOWN.\n"
              + "RETURN_ORIGINAL: just return original result;\n"
              + "MINIMIZE: find minimal CFA with same 'bad' result\n"
              + "    1. same exception by default has to be of the same class and with same\n"
              + "    first stack element;"
              + "    2. same FALSE by default has to have same target description;\n"
              + "    3. you can override outcomes allowed for the minimum in\n"
              + "    'cfaMutation.dd.minProperty';\n"
              + "MAXIMIZE: find maximal CFA with 'good' result (TRUE or FALSE by default;\n"
              + "    you can override outcomes allowed for the maximum in\n"
              + "    'cfaMutation.dd.maxProperty';\n"
              + "ISOLATE_DIFFERENCE: find a minimal difference in CFA, that causes 'bad' result\n"
              + "    when present, and 'good' result is produced with it absent;\n"
              + "SEARCH_FOR_ERROR: repeatedly find new min or max untill an error (FALSE) that is"
              + "    feasible on original program is found.")
  private CFAMutationBehavior ifException = CFAMutationBehavior.MINIMIZE;

  @Option(
      secure = true,
      description =
          "What to do if original result is TRUE, FALSE, an exception, timeout, UNKNOWN.\n"
              + "RETURN_ORIGINAL: just return original result;\n"
              + "MINIMIZE: find minimal CFA with same 'bad' result\n"
              + "    1. same exception by default has to be of the same class and with same\n"
              + "    first stack element;"
              + "    2. same FALSE by default has to have same target description;\n"
              + "    3. you can override outcomes allowed for the minimum in\n"
              + "    'cfaMutation.dd.minProperty';\n"
              + "MAXIMIZE: find maximal CFA with 'good' result (TRUE or FALSE by default;\n"
              + "    you can override outcomes allowed for the maximum in\n"
              + "    'cfaMutation.dd.maxProperty';\n"
              + "ISOLATE_DIFFERENCE: find a minimal difference in CFA, that causes 'bad' result\n"
              + "    when present, and 'good' result is produced with it absent;\n"
              + "SEARCH_FOR_ERROR: repeatedly find new min or max untill an error (FALSE) that is"
              + "    feasible on original program is found.")
  private CFAMutationBehavior ifTimeout = CFAMutationBehavior.SEARCH_FOR_ERROR_MAX;

  @Option(
      secure = true,
      description =
          "What to do if original result is TRUE, FALSE, an exception, timeout, UNKNOWN.\n"
              + "RETURN_ORIGINAL: just return original result;\n"
              + "MINIMIZE: find minimal CFA with same 'bad' result\n"
              + "    1. same exception by default has to be of the same class and with same\n"
              + "    first stack element;"
              + "    2. same FALSE by default has to have same target description;\n"
              + "    3. you can override outcomes allowed for the minimum in\n"
              + "    'cfaMutation.dd.minProperty';\n"
              + "MAXIMIZE: find maximal CFA with 'good' result (TRUE or FALSE by default;\n"
              + "    you can override outcomes allowed for the maximum in\n"
              + "    'cfaMutation.dd.maxProperty';\n"
              + "ISOLATE_DIFFERENCE: find a minimal difference in CFA, that causes 'bad' result\n"
              + "    when present, and 'good' result is produced with it absent;\n"
              + "SEARCH_FOR_ERROR: repeatedly find new min or max untill an error (FALSE) that is"
              + "    feasible on original program is found.")
  private CFAMutationBehavior ifOtherUnknown = CFAMutationBehavior.RETURN_ORIGINAL;

  @Option(
      secure = true,
      name = "dd.direction",
      description =
          "Delta Debugging can minimize input program or maximize it while preserving given property."
              + "Here properties are results of analysis run:\n"
              + "1. True;\n"
              + "2. False with same description as False for original program;\n"
              + "3. Any False;\n"
              + "4. Timeout;\n"
              + "5. Exception similar to the exception original program causes;\n"
              + "6. Any exception;\n"
              + "7. Unknown (or Not yet started) not because of exception or time limit.\n"
              + "Express property for minimized/maximized program as a set of allowed outcomes."
              + "Also DD can find minimal difference ('cause') between given properties, "
              + "I.e. if 'cause' is removed, property for minimization ceases, and property for "
              + "maximization holds. If 'cause' is present, property for minimization holds, and "
              + "property for maximization does not.\n"
              + "By default, dd minimizes for same exception, so maximization property is 'turned off'."
              + "If you specify {True, Any False} for maximization property with {Same exception} for "
              + "minimization property, DD will find a minimal difference that changes a program from "
              + "reproducing the exception to correct analysis run with conclusive verdict.\n"
              + "The two properties should be mutually exclusive (impossible together); minimization "
              + "property should hold for original input program, and maximization property should "
              + "hold for 'empty' program `int main() {return 0;}`.")
  private DDDirection ddDirection = DDDirection.NOT_SPECIFIED;

  @Option(
      secure = true,
      name = "dd.minProperty",
      description =
          "Delta Debugging can minimize input program or maximize it while preserving given property."
              + "Here properties are results of analysis run:\n"
              + "1. True;\n"
              + "2. False with same description as False for original program;\n"
              + "3. Any False;\n"
              + "4. Timeout;\n"
              + "5. Exception similar to the exception original program causes;\n"
              + "6. Any exception;\n"
              + "7. Unknown (or Not yet started) not because of exception or time limit.\n"
              + "Express property for minimized/maximized program as a set of allowed outcomes."
              + "Also DD can find minimal difference ('cause') between given properties, "
              + "I.e. if 'cause' is removed, property for minimization ceases, and property for "
              + "maximization holds. If 'cause' is present, property for minimization holds, and "
              + "property for maximization does not.\n"
              + "By default, dd minimizes for same exception, so maximization property is 'turned off'."
              + "If you specify {True, Any False} for maximization property with {Same exception} for "
              + "minimization property, DD will find a minimal difference that changes a program from "
              + "reproducing the exception to correct analysis run with conclusive verdict.\n"
              + "The two properties should be mutually exclusive (impossible together); minimization "
              + "property should hold for original input program, and maximization property should "
              + "hold for 'empty' program `int main() {return 0;}`.")
  private ImmutableSet<AnalysisOutcome> ddMinProperty;

  @Option(
      secure = true,
      name = "dd.maxProperty",
      description =
          "Delta Debugging can minimize input program or maximize it while preserving given property."
              + "Here properties are results of analysis run:\n"
              + "1. True;\n"
              + "2. False with same description as False for original program;\n"
              + "3. Any False;\n"
              + "4. Timeout;\n"
              + "5. Exception similar to the exception original program causes;\n"
              + "6. Any exception;\n"
              + "7. Unknown (or Not yet started) not because of exception or time limit.\n"
              + "Express property for minimized/maximized program as a set of allowed outcomes."
              + "Also DD can find minimal difference ('cause') between given properties, "
              + "I.e. if 'cause' is removed, property for minimization ceases, and property for "
              + "maximization holds. If 'cause' is present, property for minimization holds, and "
              + "property for maximization does not.\n"
              + "By default, dd minimizes for same exception, so maximization property is 'turned off'."
              + "If you specify {True, Any False} for maximization property with {Same exception} for "
              + "minimization property, DD will find a minimal difference that changes a program from "
              + "reproducing the exception to correct analysis run with conclusive verdict.\n"
              + "The two properties should be mutually exclusive (impossible together); minimization "
              + "property should hold for original input program, and maximization property should "
              + "hold for 'empty' program `int main() {return 0;}`.")
  private ImmutableSet<AnalysisOutcome> ddMaxProperty =
      ImmutableSet.of(AnalysisOutcome.VERDICT_TRUE, AnalysisOutcome.VERDICT_FALSE);

  @SuppressWarnings("rawtypes")
  private enum DDVariant {
    NOT_SPECIFIED(null),
    OLD(null),
    DD(FlatDeltaDebugging.class),
    DDSTAR(DDStar.class),
    DDSEARCH(DDSearch.class),
    HDD(HierarchicalDeltaDebugging.class);

    private final Class<? extends FlatDeltaDebugging> ddClass;

    DDVariant(Class<? extends FlatDeltaDebugging> pClass) {
      ddClass = pClass;
    }
  }

  @Option(
      secure = true,
      name = "dd",
      description =
          "which DD algorithm implementation to use to mutate CFA: my previous implementation of "
              + "original (flat) DD; new implementation of original (flat) DD; DD*, that iterates "
              + "cause-isolating DD to minimize or maximize; DDSearch, that iterates over optimums "
              + "found by DD*; or hierarchical DD.")
  private DDVariant ddVariant = DDVariant.NOT_SPECIFIED;

  @Option(
      secure = true,
      name = "manipulator",
      description = "which elements to remove from CFA (one manipulator class)")
  @ClassOption(packagePrefix = "org.sosy_lab.cpachecker.cfa.mutation")
  private Class<? extends CFAElementManipulator<?, ?>> manipulatorClass =
      FunctionBodyManipulator.class;

  @Option(
      secure = true,
      name = "dd.parts",
      description = "which parts to remove? DD can remove deltas, complements, or both")
  private PartsToRemove mode = PartsToRemove.DELTAS_AND_COMPLEMENTS;

  @Option(
      secure = true,
      name = "oldStrategies",
      description = "which strategies to use subsequently to mutate CFA")
  @ClassOption(packagePrefix = "org.sosy_lab.cpachecker.cfa.mutation")
  private List<Class<? extends CFAMutationStrategy>> oldStrategies =
      ImmutableList.of(
          FunctionBodyRemover.class,
          AssumeEdgesRemover.class,
          StatementChainRemover.class,
          StatementEdgeRemover.class,
          ExpressionRemover.class,
          DeclarationEdgeRemover.class,
          GlobalDeclarationRemover.class);

  @Option(
      secure = true,
      name = "dontExportLonger",
      description =
          "Time limit for exporting a CFA. If an export exceeds this limit, "
              + "CFA exports are disabled for the following rounds. "
              + "This time limit is not checked if this option is set to zero.")
  @TimeSpanOption(codeUnit = TimeUnit.SECONDS, defaultUserUnit = TimeUnit.SECONDS, min = 0)
  private TimeSpan exportTimelimit = TimeSpan.ofSeconds(2);

  @Option(secure = true, description = "Whether to export each round in different subdirectories.")
  private boolean useDifferentDirectories = false;

  @Option(description = "subdirectory of the original analysis round")
  private String directoryForOriginalRound = "0-original-round";

  private final Path mainExportDirectory;
  private final String cfaExportSubdirectory;

  boolean exportWasTooLong = false;

  /** local CFA of functions before processing */
  private FunctionCFAsWithMetadata localCfa;
  /** Strategy that decides how to change the CFA and implements this change */
  private CFAMutationStrategy strategy;

  private final CFAMutatorStatistics mutatorStats;

  private enum CanMutate {
    NEVER_MUTATED,
    CAN_MUTATE,
    CAN_NOT_MUTATE,
    UNKNOWN;
  }

  private CanMutate strategyCanMutate = CanMutate.NEVER_MUTATED;

  public CFAMutator(Configuration pConfig, LogManager pLogger, ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, pShutdownNotifier);
    config.inject(this, CFAMutator.class);
    mutatorStats = new CFAMutatorStatistics(pLogger);

    FileTypeConverter ftc =
        ((FileTypeConverter) Configuration.getDefaultConverters().get(FileOption.class));
    mainExportDirectory = ftc.getOutputPath();

    if (exportDirectory != null) {
      cfaExportSubdirectory = exportDirectory.relativize(mainExportDirectory).toString();
      if (useDifferentDirectories) {
        exportDirectory = mainExportDirectory.resolve(directoryForOriginalRound);
      }
    } else {
      cfaExportSubdirectory = null;
    }

    if (ddVariant == DDVariant.OLD) {
      // use previous impl
      if (oldStrategies.isEmpty()) {
        throw new InvalidConfigurationException("No CFA mutation strategies were specified");
      }
      strategy = buildOldStrategies(pConfig, pLogger);
      return;
    }

    // new impl
    if (ddVariant != DDVariant.NOT_SPECIFIED && ddDirection != DDDirection.NOT_SPECIFIED) {
      strategy = buildNewStrategy();
    }
  }

  private CFAMutationStrategy buildNewStrategy() throws InvalidConfigurationException {
    Preconditions.checkState(strategy == null, "Cannot build CFA mutation strategy twice");
    Preconditions.checkState(
        ddVariant != DDVariant.NOT_SPECIFIED && ddDirection != DDDirection.NOT_SPECIFIED,
        "Cannot build CFA mutation strategy as DD variant is not set");

    if (ddDirection == DDDirection.ISOLATION
        && (ddVariant == DDVariant.DDSTAR || ddVariant == DDVariant.DDSEARCH)) {
      throw new InvalidConfigurationException("DD* and DD** cannot isolate cause");
    }

    CFAElementManipulator<?, ?> manipulator = null;
    try {
      manipulator =
          manipulatorClass
              .getConstructor(Configuration.class, LogManager.class)
              .newInstance(config, logger);
    } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
      throw new InvalidConfigurationException("Can not generate CFA manipulator from option", e);
    }

    try {
      return ddVariant
          .ddClass
          .getConstructor(
              LogManager.class, CFAElementManipulator.class, DDDirection.class, PartsToRemove.class)
          .newInstance(logger, manipulator, ddDirection, mode);

    } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
      logger.logUserException(
          Level.SEVERE, e.getCause(), "Can not generate DD CFA mutation strategy from option");
      throw new InvalidConfigurationException(
          "Can not generate DD CFA mutation strategy from option", e.getCause());
    }
  }

  private CFAMutationStrategy buildOldStrategies(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    ImmutableList.Builder<CFAMutationStrategy> strategiesList = ImmutableList.builder();
    for (Class<? extends CFAMutationStrategy> cls : oldStrategies) {
      try {
        if (cls == FunctionBodyRemover.class) {
          strategiesList.add(new FunctionBodyRemover(pConfig, pLogger));
        } else if (cls == ExpressionRemover.class) {
          strategiesList.add(new ExpressionRemover(pConfig, pLogger));
        } else {
          strategiesList.add(cls.getConstructor(LogManager.class).newInstance(pLogger));
        }
      } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
        throw new InvalidConfigurationException(
            "Can not generate CFA mutation strategies list from option", e);
      }
    }

    if (oldStrategies.size() == 1) {
      return strategiesList.build().get(0);
    } else {
      return new CompositeCFAMutationStrategy(pLogger, strategiesList.build());
    }
  }

  public void setLogger(LogManager pLogger) {
    logger = pLogger;
  }

  private boolean startExport() {
    if (strategyCanMutate != CanMutate.NEVER_MUTATED) {
      exportDirectory =
          ((FileTypeConverter) Configuration.getDefaultConverters().get(FileOption.class))
              .getOutputPath();
    }
    if (exportDirectory == null || cfaExportSubdirectory == null || exportWasTooLong) {
      return false;
    }
    exportDirectory = exportDirectory.resolve(cfaExportSubdirectory);
    mutatorStats.startExport();
    return true;
  }

  private void stopExport() {
    mutatorStats.stopExport();

    if (exportTimelimit.compareTo(TimeSpan.empty()) > 0
        && mutatorStats.getMaxExportTime().compareTo(exportTimelimit) > 0) {
      logger.log(
          Level.WARNING,
          "Following exports are disabled because they take longer than",
          exportTimelimit);
      exportWasTooLong = true;
    }
  }

  @Override
  protected void exportCFA(CFA pCfa) {
    if (!startExport()) {
      return;
    }
    super.exportCFA(pCfa);
    stopExport();
  }

  private CFA exportCreateExportCFA()
      throws InvalidConfigurationException, InterruptedException, ParserException {

    if (startExport()) {
      try {
        new DOTBuilder2(localCfa)
            .writeGraphs(exportDirectory.resolve("function-cfas-before-processing"));
      } catch (IOException e) {
        logger.logUserException(
            Level.WARNING, e, "Could not write function CFAs before processing to dot files");
      }
      // new BlockToDotWriter(localCfa).dump(exportDirectory.resolve("function-cfa-blocks"),
      // logger);
      stopExport();
    }

    mutatorStats.setCfaStats(localCfa);
    CFA result = super.createCFA(localCfa.copyAsParseResult(), localCfa.getMainFunction());
    mutatorStats.setCfaStats(result);

    exportCFA(result);
    return result;
  }

  /**
   * Use once while parsing the source, initialize CFA that will be mutated. Use {@link
   * CFACreator#createCFA} to fully create CFA from function CFA.
   */
  @Override
  protected CFA createCFA(ParseResult pParseResult, FunctionEntryNode pMainFunction)
      throws InvalidConfigurationException, InterruptedException, ParserException {
    logger.log(Level.FINE, "Creating original CFA...");
    localCfa =
        FunctionCFAsWithMetadata.fromParseResult(
            pParseResult, machineModel, pMainFunction, language);
    return exportCreateExportCFA();
  }

  @Override
  protected void exportCFAAsync(CFA pCfa) {
    // do not export asynchronously as CFA will be mutated
  }

  public void clearCreatorStats() {
    // reset CFACreator stats so following variable classification
    // and pointer resolving wont add a lot of similar stats to print
    stats = new CFACreatorStatistics(logger);
  }

  public boolean canMutate() {
    if (strategyCanMutate == CanMutate.CAN_MUTATE) {
      return true;
    } else if (strategyCanMutate == CanMutate.CAN_NOT_MUTATE) {
      return false;
    }

    // clear processings before first #canMutate and after rollbacks
    logger.log(Level.FINE, "Preparing CFA of functions for mutation...");
    mutatorStats.startCfaReset();
    localCfa.resetEdgesInNodes();
    mutatorStats.stopCfaReset();

    mutatorStats.startPreparations();
    boolean result = strategy.canMutate(localCfa);
    strategyCanMutate = result ? CanMutate.CAN_MUTATE : CanMutate.CAN_NOT_MUTATE;
    mutatorStats.stopPreparations();

    if (strategy instanceof AbstractDeltaDebuggingStrategy<?> && startExport()) {
      ((AbstractDeltaDebuggingStrategy<?>) strategy)
          .exportGraph(exportDirectory.resolve("strategy-export"));
      stopExport();
    }

    return result;
  }

  /** Apply some mutation to the CFA */
  public CFA mutate() throws InterruptedException, InvalidConfigurationException, ParserException {
    logger.log(Level.FINE, "Mutating CFA...");
    mutatorStats.startMutation();
    strategyCanMutate = CanMutate.UNKNOWN;
    strategy.mutate(localCfa);
    mutatorStats.stopMutation();

    return exportCreateExportCFA();
  }

  /** Undo last mutation if needed */
  public CFA setResult(AnalysisOutcome pLastOutcome)
      throws InvalidConfigurationException, InterruptedException, ParserException {

    // XXX write result?
    // undo createCFA before possible mutation rollback
    logger.log(Level.FINE, "Preparing CFA of functions for possible rollback...");
    mutatorStats.startCfaReset();
    localCfa.resetEdgesInNodes();
    mutatorStats.stopCfaReset();

    DDResultOfARun ddResult = outcomeToDDResult(pLastOutcome);
    mutatorStats.startAftermath(pLastOutcome, ddResult);
    MutationRollback mutationRollback = strategy.setResult(localCfa, ddResult);
    mutatorStats.stopAftermath();

    if (mutationRollback == MutationRollback.NO_ROLLBACK) {
      return null;
    }

    // export after rollback as there may be no more mutations
    mutatorStats.setCfaStats(localCfa);
    CFA rollbackedCfa = super.createCFA(localCfa.copyAsParseResult(), localCfa.getMainFunction());
    mutatorStats.setCfaStats(rollbackedCfa);

    exportCFA(rollbackedCfa);

    return mutationRollback == MutationRollback.ROLLBACK ? rollbackedCfa : null;
  }

  public CFAMutatorStatistics getCfaMutationStatistics() {
    return mutatorStats;
  }

  public String getApproachName() {
    switch (ddDirection) {
      case NOT_SPECIFIED:
        Multimap<CFAMutationBehavior, AnalysisOutcome> what =
            ImmutableListMultimap.of(
                ifTrue, AnalysisOutcome.VERDICT_TRUE,
                ifFalse, AnalysisOutcome.VERDICT_FALSE,
                ifException, AnalysisOutcome.FAILURE_BECAUSE_OF_EXCEPTION,
                ifTimeout, AnalysisOutcome.VERDICT_UNKNOWN_BECAUSE_OF_TIMEOUT,
                ifOtherUnknown, AnalysisOutcome.ANOTHER_VERDICT_UNKNOWN);
        return Joiner.on(", ")
            .join(
                FluentIterable.from(what.keySet())
                    .transform(k -> k.toString() + " for " + Joiner.on(", ").join(what.get(k))));

      case ISOLATION:
        assert ddMaxProperty != null
            : "Maximization property should be explicitly set for isolating cause";
        return Joiner.on(' ')
            .join(
                "isolate what in CFA causes a change from",
                ddMaxProperty,
                "to",
                ddMinProperty == null ? "original result" : ddMinProperty);

      case MAXIMIZATION:
        return "maximize CFA for " + (ddMaxProperty == null ? "original result" : ddMaxProperty);

      case MINIMIZATION:
        return "minimize CFA for " + (ddMinProperty == null ? "original result" : ddMinProperty);

      default:
        throw new AssertionError();
    }
  }

  public void verifyOutcome(AnalysisOutcome pAnalysisOutcome) {
    if (ddDirection == DDDirection.MAXIMIZATION && !canMutate()) {
      Verify.verify(
          ddMaxProperty.contains(pAnalysisOutcome),
          "max-property after maximization was finished %s does not hold: %s",
          ddMaxProperty,
          pAnalysisOutcome);
    } else {
      Verify.verify(
          ddMinProperty.contains(pAnalysisOutcome),
          "min-property %s does not hold: %s",
          ddMinProperty,
          pAnalysisOutcome);
    }
  }

  private DDResultOfARun outcomeToDDResult(AnalysisOutcome pOutcome) {
    boolean inMin = ddMinProperty.contains(pOutcome);
    boolean inMax = ddMaxProperty.contains(pOutcome);
    Verify.verify(!inMax || !inMin, "Properties should be mutually exclusive.");
    if (inMin) {
      return DDResultOfARun.MINIMIZATION_PROPERTY_HOLDS;
    } else if (inMax) {
      return DDResultOfARun.MAXIMIZATION_PROPERTY_HOLDS;
    } else {
      return DDResultOfARun.NEITHER_PROPERTY_HOLDS;
    }
  }

  private ImmutableSet<AnalysisOutcome> normalize(ImmutableSet<AnalysisOutcome> pOutcomes) {
    ImmutableSet.Builder<AnalysisOutcome> builder =
        ImmutableSet.<AnalysisOutcome>builder().addAll(pOutcomes);

    if (pOutcomes.contains(AnalysisOutcome.FAILURE_BECAUSE_OF_EXCEPTION)) {
      builder.add(AnalysisOutcome.FAILURE_BECAUSE_OF_SAME_EXCEPTION);
    }

    if (pOutcomes.contains(AnalysisOutcome.VERDICT_FALSE)) {
      builder.add(AnalysisOutcome.SAME_VERDICT_FALSE);
    }

    return builder.build();
  }

  private boolean areMutuallyExclusive(
      ImmutableSet<AnalysisOutcome> pLeft, ImmutableSet<AnalysisOutcome> pRight) {
    return !pLeft.stream().anyMatch(outcome -> pRight.contains(outcome));
  }

  public boolean isSearchingForError() {
    return ddVariant == DDVariant.DDSEARCH;
  }

  public boolean shouldReturnOriginalResultAlsoBuildStrategy(AnalysisOutcome pOriginalOutcome)
      throws InvalidConfigurationException {
    CFAMutationBehavior behave;

    switch (pOriginalOutcome) {
      case VERDICT_TRUE:
        behave = ifTrue;
        break;
      case SAME_VERDICT_FALSE:
        behave = ifFalse;
        break;
      case FAILURE_BECAUSE_OF_SAME_EXCEPTION:
        behave = ifException;
        break;
      case VERDICT_UNKNOWN_BECAUSE_OF_TIMEOUT:
        behave = ifTimeout;
        break;
      case ANOTHER_VERDICT_UNKNOWN:
        behave = ifOtherUnknown;
        break;
      default:
        throw new AssertionError();
    }

    logger.log(Level.INFO, behave, "for", pOriginalOutcome);
    boolean result = behave == CFAMutationBehavior.RETURN_ORIGINAL;

    if (result) {
      return true;
    }

    if (ddMinProperty == null) {
      ddMinProperty = ImmutableSet.of(pOriginalOutcome);
    } else {
      ddMinProperty = normalize(ddMinProperty);
    }
    ddMaxProperty = normalize(ddMaxProperty);

    if (ddMinProperty.isEmpty() && ddMaxProperty.isEmpty()) {
      logger.log(
          Level.SEVERE, "Properties", ddMinProperty, "and", ddMaxProperty, "must not be empty");
      return true;
    }

    if (!areMutuallyExclusive(ddMinProperty, ddMaxProperty)) {
      logger.log(
          Level.SEVERE, "Properties", ddMinProperty, "and", ddMaxProperty, "must not intersect");
      return true;
    }

    if (ddMaxProperty.contains(pOriginalOutcome)) {
      logger.log(
          Level.SEVERE,
          "CFA is not mutated as original outcome",
          pOriginalOutcome,
          "is 'good' (in 'pass' maximization property).");
      return true;
    }

    Verify.verify(
        ddMinProperty.contains(pOriginalOutcome),
        "min-property %s does not hold: %s",
        ddMinProperty,
        pOriginalOutcome);

    if (ddVariant == DDVariant.NOT_SPECIFIED) {
      chooseDDVariantAndBuildStrategy(behave);
    }
    return false;
  }

  private void chooseDDVariantAndBuildStrategy(CFAMutationBehavior pBehave)
      throws InvalidConfigurationException {
    Preconditions.checkState(
        ddVariant == DDVariant.NOT_SPECIFIED
            && ddDirection == DDDirection.NOT_SPECIFIED
            && strategy == null,
        "CFA mutation strategy must not be overriden");

    switch (pBehave) {
      case ISOLATE_DIFFERENCE:
        ddVariant = DDVariant.DD;
        ddDirection = DDDirection.ISOLATION;
        break;
      case MAXIMIZE:
        ddVariant = DDVariant.DD;
        ddDirection = DDDirection.MAXIMIZATION;
        break;
      case MINIMIZE:
        ddVariant = DDVariant.DD;
        ddDirection = DDDirection.MINIMIZATION;
        break;
      case SEARCH_FOR_ERROR_MAX:
        ddVariant = DDVariant.DDSEARCH;
        ddDirection = DDDirection.MAXIMIZATION;
        break;
      case SEARCH_FOR_ERROR_MIN:
        ddVariant = DDVariant.DDSEARCH;
        ddDirection = DDDirection.MINIMIZATION;
        break;
      case RETURN_ORIGINAL:
      default:
        throw new AssertionError();
    }

    strategy = buildNewStrategy();
  }

  public CFA restoreCfa()
      throws InvalidConfigurationException, InterruptedException, ParserException {
    Pair<ParseResult, FunctionEntryNode> pair = FunctionCFAsWithMetadata.originalCopy();
    return super.createCFA(pair.getFirst(), pair.getSecond());
  }
}
