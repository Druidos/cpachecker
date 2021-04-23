// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.coverage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Map;
import org.sosy_lab.cpachecker.util.coverage.FileCoverageInformation.FunctionInfo;

public class AdditionalCoverageReportGcov {

  // String constants from gcov format
  private static final String TESTNAME = "TN:";
  private static final String SOURCEFILE = "SF:";
  private static final String FUNCTION = "FN:";
  private static final String FUNCTIONDATA = "FNDA:";
  private static final String LINEDATA = "DA:";
  private static final String ADDITIONAL = "ADD:";

  public static void write(CoverageData pCoverage, Writer w) throws IOException {

    for (Map.Entry<String, FileCoverageInformation> entry :
        pCoverage.getInfosPerFile().entrySet()) {
      String sourcefile = entry.getKey();
      FileCoverageInformation fileInfos = entry.getValue();

      // Convert ./test.c -> /full/path/test.c
      w.append(TESTNAME + "\n");
      w.append(SOURCEFILE + Path.of(sourcefile).toAbsolutePath() + "\n");

      for (FunctionInfo info : fileInfos.allFunctions) {
        w.append(FUNCTION + info.firstLine + "," + info.name + "\n");
        // Information about function end isn't used by lcov, but it is useful for some
        // postprocessing
        // But lcov ignores all unknown lines, so, this additional information can't affect on its
        // work
        w.append("#" + FUNCTION + info.lastLine + "\n");
      }

      for (Multiset.Entry<String> functionEntry : fileInfos.visitedFunctions.entrySet()) {
        w.append(FUNCTIONDATA + functionEntry.getCount() + "," + functionEntry.getElement() + "\n");
      }

      /* Now save information about lines
       */
      for (Integer line : fileInfos.allLines) {
        w.append(LINEDATA + line + "," + fileInfos.getVisitedLine(line) + "\n");
        ImmutableSet<String> strings = fileInfos.additionalInfo.get(line);
        if (!strings.isEmpty()) {
          w.append(ADDITIONAL + String.join(", ", strings) + "\n");
        }
      }
      w.append("end_of_record\n");
    }
  }
}
