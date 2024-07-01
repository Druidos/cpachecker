#!/usr/bin/env bash
#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0

echo '
.integration-tests: &integration-tests
  # cancel whole pipeline on a new commit on the same ref
  interruptible: true
  tags:
    - docker
  stage: test
  image: ${CI_REGISTRY_IMAGE}/test:jdk-11
  needs:
    - pipeline: $PARENT_PIPELINE_ID
      job: build-dependencies
    - pipeline: $PARENT_PIPELINE_ID
      job: build:jdk-11
  before_script:
    - ln -sv /test/programs/benchmarks test/programs/benchmarks
    - ln -sv /test/programs/ldv-benchmarks test/programs/ldv-benchmarks
  artifacts:
    paths:
      - "*.csv"
    when: always

' > generated-ci.yml

TESTS="gitlab-ci/test-set/*-30s.*.csv"
TESTS+=" gitlab-ci/test-set/*-120s.*.csv"
TESTS+=" gitlab-ci/test-set/*-180s.*.csv"

for TEST_SET in $TESTS
do
  echo $TEST_SET
  TEST=${TEST_SET#gitlab-ci/test-set/}
  echo python3 gitlab-ci/run-tests.py "$TEST"
  {
    echo $TEST:
    echo "  <<: *integration-tests"
    echo "  script:"
    echo "    - python3 gitlab-ci/run-tests.py $TEST"
    if [[ $TEST =~ "flaky" ]]  # these tend to be very flaky
    then echo "  allow_failure: true"
    fi
    echo ""
  } >> generated-ci.yml
done
