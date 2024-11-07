// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

int main() {
  int __attribute__((__aligned__)) no_pars;
  int __attribute__((__aligned__())) empty_pars;
  int __attribute__((__aligned__(__BIGGEST_ALIGNMENT__))) biggest;
  int __attribute__((__aligned__(2))) a2;
  int __attribute__((__aligned__(0x2))) x2;
  int __attribute__((__aligned__(02))) o2;
  int __attribute__((__aligned__(+2))) p2;
  int __attribute__((__aligned__(2+2))) a4;
  int __attribute__((__aligned__(_Alignof(long long)))) of_llong;
  // TODO
  // int __attribute__((__aligned__(_Alignof(a2)))) of_var;
}
