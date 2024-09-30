This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
SPDX-License-Identifier: Apache-2.0

The files in this directory are used in the CI of the ISPRAS fork.
Longer tests are in ./long subdirectory, so they are not used on every push.

All tests from SV-COMP memsafety subcategories are completely included in
short-memsafety.xml, software-systems-memsafety.xml, and ./long/long-memsafety.xml
-- except Juliet tests, which are chosen pseudorandomly for juliet-every-251th.set
and ./long/CWE*.set, as amount of Juliet tasks with memsafety properties is 18K files.
The tasks are not repeated between Juliet subsets.
Note, SV-COMP uses just a few of these CWEs, and only tasks without bugs,
as for the tasks with bugs there is no violated subproperties specified.

Memcleanup includes all files from SV-COMP MemCleanup category (in files
juliet-memcleanup.set and other-memcleanup.set).

./drivers.xml includes all respective drivers as in LDV-Benchmarks repo;
drivers for ./long/linux-4-18-rc5.xml are chosen pseudorandomly from respective sets.
