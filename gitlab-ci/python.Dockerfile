# This file is part of CPAchecker,
# a tool for configurable software verification:
# https://cpachecker.sosy-lab.org
#
# SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
# SPDX-License-Identifier: Apache-2.0

FROM python:3.8
RUN pip install 'black[jupyter]<25' 'flake8<6' flake8-awesome defusedxml pyyaml
ENTRYPOINT [ "" ]
