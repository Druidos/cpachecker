#!/usr/bin/env python3

#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0

# This is copy of scripts/table-generator with hacked get_regression_count
# to print more details (i.e. count improvements).

import collections
# This file is part of CPAchecker,
# a tool for configurable software verification:
# https://cpachecker.sosy-lab.org
#
# SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
#
# SPDX-License-Identifier: Apache-2.0

import glob
import os
import sys

from benchexec import result

sys.dont_write_bytecode = True  # prevent creation of .pyc files
for egg in glob.glob(
    os.path.join(
        os.path.dirname(__file__), os.pardir, "lib", "python-benchmark", "*.whl"
    )
):
    sys.path.insert(0, egg)

# Add ./benchmark/tools to __path__ of benchexec.tools package
# such that additional tool-wrapper modules can be placed in this directory.
import benchexec.tools

benchexec.tools.__path__ += [
    os.path.join(os.path.dirname(__file__), "benchmark", "tools")
]

import benchexec.tablegenerator

benchexec.tablegenerator.DEFAULT_OUTPUT_PATH = "test/results/"
benchexec.tablegenerator.LIB_URL = "https://www.sosy-lab.org/lib"


def print_counts(rows, ignore_flapping_timeouts):
    """Hacking improvements/changes/regressions count instead of get_regression_count"""
    # include get_counts with one-word categorization
    # note, main() still calls old get_counts, which dumps similar numbers without description
    for runResults in benchexec.tablegenerator.rows_to_columns(rows):
        counts = collections.Counter(runResult.category for runResult in runResults)
        print(", ".join((k or "none") + " %d" % v for k, v in counts.items()))

    def status_is(run_result, status):
        # startswith is used because status can be "TIMEOUT (TRUE)" etc., which count as "TIMEOUT"
        return run_result.status and run_result.status.startswith(status)

    def any_status_is(run_results, status):
        for run_result in run_results:
            if status_is(run_result, status):
                return True
        return False

    regressions = 0
    problem_changes = 0
    improvements = 0
    same = 0
    flaky = 0
    for row in rows:
        if len(row.results) < 2:
            # no regressions at all with only one run
            break

        # "new" and "old" are the latest two results
        new = row.results[-1]
        old = row.results[-2]

        if new.status == old.status:
            same += 1
            continue  # no regression/improvement if result is the same as before
        if status_is(new, "TIMEOUT") and status_is(old, "TIMEOUT"):
            same += 1
            continue  # no regression if both are some form of timeout
        if status_is(new, "OUT OF MEMORY") and status_is(old, "OUT OF MEMORY"):
            same += 1
            continue  # same for OOM

        if (
                ignore_flapping_timeouts
                and status_is(new, "TIMEOUT")
                and any_status_is(row.results[:-2], "TIMEOUT")
        ):
            flaky += 1
            continue  # flapping timeout because any of the older results is also a timeout

        if new.category == result.CATEGORY_CORRECT:
            improvements += 1
        elif old.category == result.CATEGORY_CORRECT:
            regressions += 1
        else:
            problem_changes += 1

    red_color = '\033[1;31m'
    green_color = '\033[1;32m'
    no_color = '\033[0m'  # No Color

    total = len(rows)
    print("\nStatistics for this run:")
    right = counts[result.CATEGORY_CORRECT]
    print(f"right verdicts: {right:3d} / {total} ({100 * right / total:2.0f}%)")
    wrong = counts[result.CATEGORY_WRONG]
    print(f"wrong verdicts: {wrong:3d} / {total} ({100 * wrong / total:2.0f}%)")
    probs = total - right - wrong
    print(f"other problems: {probs:3d} / {total} ({100 * probs / total:2.0f}%)")
    print("\nChanges from previous run:")
    print(f"same status:       {same:3d}")
    print(f"improvements:      {improvements:3d}")
    print(f"changed problems:  {problem_changes:3d}")
    print(f"flaky timeouts:    {flaky:3d}")
    print(f"regressions:       {regressions:3d}\n")
    if regressions > 0:
        sys.exit(f"{red_color}There are {regressions} regressions, "
                 f"{problem_changes} changed problems, "
                 f"and {improvements} improvements{no_color}.\n")
    elif problem_changes > 0:
        print(f"{red_color}There are no lost verdicts, but "
              f"{problem_changes} changed problems and "
              f"{improvements} improvements{no_color}.\n")
        sys.exit(116)  # inspired by EUCLEAN
    elif improvements > 0:
        print(f"{red_color}There are no regressions and {improvements} improvements{no_color}.\n")
        sys.exit(116)  # inspired by EUCLEAN
    elif flaky > 0:
        print(f"{green_color}There are no changes in results, "
              f"except {flaky} flapping timeouts{no_color}.\n")
    else:
        print(f"{green_color}There are no changes in results{no_color}.\n")
    return regressions


benchexec.tablegenerator.get_regression_count = print_counts


if __name__ == "__main__":
    try:
        sys.exit(benchexec.tablegenerator.main())
    except KeyboardInterrupt:
        print("Interrupted")
