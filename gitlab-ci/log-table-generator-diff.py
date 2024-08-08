#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0


import sys


red_color = '\033[1;31m'
green_color = '\033[1;32m'
no_color = '\033[0m'  # No Color


if __name__ == '__main__':
    # first line is backwards 'regressions', i.e. improvements
    line = next(sys.stdin).strip()
    assert line.startswith("REGRESSIONS "), "expected 'REGRESSIONS %d' in line 1, got " + line
    regressions = int(line.split()[1])
    line = next(sys.stdin).strip()
    assert line == "STATS", "expected 'STATS' in line 2, got " + line
    for next_line in sys.stdin:
        prev_line, line = line, next_line
    prev_right, _, _ = prev_line.split()  # previous stats are in last line
    right, wrong, errors = line.split()  # current stats are in last line
    prev_right, right, wrong, errors = int(prev_right), int(right), int(wrong), int(errors)
    total = right + wrong + errors
    improvements = right - prev_right
    if improvements < 0:
        improvements = 0
    print(f"right verdicts: {right:2d} / {total} ({100 * right / total:2.0f}%)")
    print(f"wrong verdicts: {wrong:2d} / {total} ({100 * wrong / total:2.0f}%)")
    print(f"problems:       {errors:2d} / {total} ({100 * errors / total:2.0f}%)")
    if regressions > 0:
        sys.exit(f"{red_color}there are {regressions} regressions "
                 f"and {improvements} improvements{no_color}")
    elif improvements > 0:
        print(f"{red_color}there are no regressions "
              f"and {improvements} improvements{no_color}")
        sys.exit(116)  # inspired by EUCLEAN
    else:
        print(f"{green_color}there are no changes in results{no_color}")
