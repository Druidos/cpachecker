# This file is part of CPAchecker,
# a tool for configurable software verification:
# https://cpachecker.sosy-lab.org
#
# SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
#
# SPDX-License-Identifier: Apache-2.0

# This is a Docker image for running CI for CPAchecker. It has
#
# Commands for updating the image:
# 1. copy only necessary parts into gitlab-ci/test
#    (assuming up-to-date benchmarks linked as expected)
# copy-from-benchmarks.py
# 2. build the image using this file
# docker build --pull -t gitlab.ispras.ru:4567/verification/cpachecker/test:jdk-11 -f gitlab-ci/jdk11-with-benchexec.Dockerfile ./gitlab-ci/
# 3. push it
# docker push gitlab.ispras.ru:4567/verification/cpachecker/test:jdk-11

FROM registry.gitlab.com/sosy-lab/software/java-project-template/test:jdk-11
RUN apt-get update && apt-get install -y \
  gcc \
  graphviz \
  libgmp10 \
  libgomp1 \
  python3 \
  clang-9 \
  software-properties-common
RUN add-apt-repository ppa:sosy-lab/benchmarking
RUN apt-get update && apt-get install -y --no-install-recommends benchexec

COPY test/ /test/
