# This file is part of CPAchecker,
# a tool for configurable software verification:
# https://cpachecker.sosy-lab.org
#
# SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
#
# SPDX-License-Identifier: Apache-2.0


"""
Package for generating programs to test CPAchecker alignment calculation. Programs
consist of asserts or prints for expressions, that are generated for a single variable,
see ``graph`` module.

Expressions and operators are defined in ``expressions`` module, Number, pointer and
array C types are defined in ``ctypes``, two machine models (64- and 32-bit) are defined
in ``machines``.
"""
