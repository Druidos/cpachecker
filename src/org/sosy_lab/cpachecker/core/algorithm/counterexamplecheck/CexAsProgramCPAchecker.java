// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.Appender;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.io.TempFile;
import org.sosy_lab.common.io.TempFile.TempFileBuilder;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.CPAchecker;
import org.sosy_lab.cpachecker.core.CPAcheckerMutator;
import org.sosy_lab.cpachecker.core.CPAcheckerMutator.AnalysisResult;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CounterexampleAnalysisFailed;
import org.sosy_lab.cpachecker.util.cwriter.PathToCForCpacheckerTranslator;

/** Exports C code as for CBMC, checks the program as usual CPAchecker. */
@Options(prefix = "cfaMutation.cex.checker")
public class CexAsProgramCPAchecker extends CounterexampleChecker {
  private final CPAchecker cpachecker;
  private AnalysisResult foundFalse;

  @Option(secure = true, name = "file", description = "file to dump counterexample to")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate fileTemplate =
      PathTemplate.ofFormatString("counterexample-with-restored-functions.%d.c");

  public CexAsProgramCPAchecker(
      Configuration pConfig, LogManager pLogger, ShutdownManager pShutdownManager)
      throws InvalidConfigurationException {
    pConfig.inject(this, CexAsProgramCPAchecker.class);
    cpachecker = new CPAchecker(pConfig, pLogger, pShutdownManager);
  }

  @Override
  protected boolean checkCounterexample0(
      ARGState pRootState, ARGState pErrorState, Set<ARGState> pErrorPathStates, Path cFile)
      throws CounterexampleAnalysisFailed, InterruptedException {

    AnalysisResult result = CPAcheckerMutator.analysisRound(cpachecker, cFile.toString());

    switch (result.getVerdict()) {
      case FALSE:
        foundFalse = result;
        return true;

      case TRUE:
        return false;

      case NOT_YET_STARTED:
      case UNKNOWN:
        throw new CounterexampleAnalysisFailed(result.getDescription(), result.getThrown());

      case DONE:
        throw new CounterexampleAnalysisFailed("Counterexample was not analyzed");

      default:
        throw new AssertionError("Unexpected result case: " + result.getVerdict());
    }
  }

  // copied from {@link CBMCChecker}
  @Override
  protected void writeCexFile(
      ARGState pRootState, ARGState pErrorState, Set<ARGState> pErrorPathStates, Path pFile)
      throws CounterexampleAnalysisFailed, InterruptedException {
    assert pFile != null;

    Appender pathProgram =
        PathToCForCpacheckerTranslator.translatePaths(pRootState, pErrorPathStates);

    // write program to disk
    try (Writer w = IO.openOutputFile(pFile, Charset.defaultCharset())) {
      pathProgram.appendTo(w);

    } catch (IOException e) {
      throw new CounterexampleAnalysisFailed(
          "Could not write path program to file " + e.getMessage(), e);
    }
  }

  @Override
  protected TempFileBuilder getTempFileBuilder() {
    return TempFile.builder().prefix("cexWithRestoredFunctions").suffix(".c");
  }

  @Override
  protected @Nullable PathTemplate getCexFileTemplate() {
    return fileTemplate;
  }

  public AnalysisResult getFoundFalse() {
    Preconditions.checkState(foundFalse != null);
    return foundFalse;
  }
}
