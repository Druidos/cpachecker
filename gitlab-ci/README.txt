This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
SPDX-License-Identifier: Apache-2.0

The test-sets/*.csv files represent test sets used with ci-tests.py script in our
GitLab CI as we cannot use BenchExec. The script gitlab-ci/results-to-expect.py
produces a .csv file like this from one made by BenchExec.

The config/*.config files represent options for CPAchecker. One of these is used
with a test-set based on the test-set prefix.
