This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
SPDX-License-Identifier: Apache-2.0

The docker file describes the image used in our CI. The files used in the benchmarks
as integration tests are added via copy-from-benchmarks.py to the image.

The integration tests run as a child pipeline generated via
check-and-generate-integration-tests-pipeline.py.
Pipeline triggered by push has smaller tests (see test/test-sets/gitlab-ci),
pipeline triggered manually via web has all tests
(including those in test/test-sets/gitlab-ci/long).

There are previous (i.e. expected) results for those test sets produced
by BenchExec and composed in tables in ./previous-results subdirectory
by separate-flaky-timeouts.py. If there are flapping timeouts, both
$testset.$rundef.$tasks.previous-results.xml.bz2 and
$testset.$rundef.$tasks.timeouts.xml.bz2 are stored and used by table-generator
to better check regressions in CI.


*** HOW TO ADD TESTS ***

1. Use test-set-template-for-smg.xml to add new test sets to run in CI.

2. Add respective .xml in test/test-sets/gitlab-ci[/long], commit and push.

3. Update :jdk-11 image by starting CI jobs 'get-partial-benchmarks'
   and 'build-docker:test:jdk-11' manually.

Note: You can cancel other jobs or use results for your new tests from the same pipeline.
   The jobs can take ~20 min because of SV- and LDV-benchmarks download,
   so don't update the image if you know all files are already there.
   Alternatively, prepare docker image locally without using CI jobs
   (see comments in jdk11-with-benchexec.Dockerfile).

4. Copy a result .xml.bz2 (or a few) for each rundef.tasks pair
   from the new testset into ./previous-results/to-parse.

5. Run `python3 gitlab-ci/separate-flaky-timeouts.py`. It should match files
   automatically (both your new ones and possible previous results) and update the results.

6. Commit new or changed ./previous-results/*.xml.bz2 files.

Done: You have added a test set and expected results to check regressions against.
