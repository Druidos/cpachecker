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

// An enumeration type (enum) has an unsigned underlying
// (compatible) type if possible, i.e. if it has no negative
// values (in GCC and Clang).
// But enumeration constants (enumerators) are of type
// signed int (C11§6.7.2.2 2) if possible.
// Both GCC and Clang allow values that do not fit in int
// and thus break the C11 standard.

enum UnsignedEnum {
  plusOne = 1, // signed constant
} unsignedVar; // unsigned enum

enum SignedEnum {
  minusOne = -1, // signed constant
} signedVar; // signed enum

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
  assert(minusOne < 0);

  return 0;
}
