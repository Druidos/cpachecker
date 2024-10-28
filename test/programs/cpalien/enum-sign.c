// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

void assert(int cond) {
  if (!cond) {
    int *p;
    free(p);
  }
}

// Enums are unsigned if possible (in GCC and Clang),
// but enumerators are signed int (C11§6.7.2.2 2) if possible.

enum UnsignedEnum {
  plusOne = 1,
} unsignedVar;

enum SignedEnum {
  minusOne = -1,
} signedVar;

int main () {
  unsignedVar = -1ll;
  signedVar = -1ll;

  assert(unsignedVar > 0);
  assert(signedVar < 0);

  // signed+signed = signed
  // [un]signed+[un]signed = unsigned otherwise
  unsigned a = 1;
  assert(a - 2 > 0);
  assert(plusOne - 2 < 0);

  return 0;
}
