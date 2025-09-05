# This file is part of CPAchecker,
# a tool for configurable software verification:
# https://cpachecker.sosy-lab.org
#
# SPDX-FileCopyrightText: 2025 Dirk Beyer <https://www.sosy-lab.org>
#
# SPDX-License-Identifier: Apache-2.0

# This is a Docker image for running the CPALockator CI.
#
# Commands for updating the image:
# 1. build the image using this file
# docker build --pull -t gitlab.ispras.ru:4567/verification/cpachecker/cpalockator:jdk-17 - < build/cpalockator.jdk-17.Dockerfile
# 2. push it
# docker push gitlab.ispras.ru:4567/verification/cpachecker/cpalockator:jdk-17

FROM registry.gitlab.com/sosy-lab/software/cpachecker/test:jdk-17

# install pyyaml... so install pip first. cleaning apt lists afterwards is considered good
RUN apt-get update \
    && apt-get install -y --no-install-recommends pip \
    && pip3 install pyyaml \
    && rm -rf /var/lib/apt/lists/*

RUN git clone https://gitlab.com/sosy-lab/software/ldv-klever-benchmarks.git /opt/ldv-klever-benchmarks/
# ADD linux-4.2.6-races.tar.xz /opt/ldv-benchmarks/
RUN mkdir /opt/sv-benchmarks/
RUN curl -L https://nextcloud.ispras.ru/index.php/s/BXc2qdSLPNcYaCx/download/ldv-sv-benchmarks.tar.xz -o /opt/ldv-sv-benchmarks.tar.xz \
    && tar -xaf /opt/ldv-sv-benchmarks.tar.xz -C /opt/sv-benchmarks/ && rm -rf /opt/ldv-sv-benchmarks.tar.xz
# ADD ldv-sv-benchmarks.tar.xz /opt/sv-benchmarks/
RUN git clone https://github.com/mutilin/uppsala.git /opt/uppsala/
# RUN git clone https://gitlab.com/sosy-lab/software/ldv-benchmarks.git /opt/ldv-benchmarks/
# RUN git clone https://github.com/sosy-lab/sv-benchmarks /opt/sv-benchmarks/
# The repo is very large, save memory and get only necessary directory
# RUN mv /opt/sv-benchmarks/c/ldv-linux-3.14-races/ /opt/ && \
#    mv /opt/sv-benchmarks/c/ldv-races/ /opt/ && \
#    rm -rf /opt/sv-benchmarks/