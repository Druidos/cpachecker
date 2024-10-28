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

// Enumerators are signed int (C11§6.7.2.2 2),
// but GCC and Clang allow enumerators that don't fit in int.
// Those get the same type as their enum.
// Packed attribute does not make enumerators smaller than int.

enum EnumTag {
  // The value is max of signed int, the enumerator gets type int.
  intMax = ~0u >> 1,
  // GCC fails, Clang warns of overflow if the value is omitted (+1 by default).
  // Both warn of overflow if +1 is written instead of +1L.
  // The value fits in unsigned int, but the enumerator is ULL, same as enum.
  intMax1 = intMax + 1L,
  intMax2,
  // The value is max of signed long long, the enumerator is ULL, same as enum.
  llMax = ~0ull >> 1,
} enumVar; // The enum is unsigned long long.

int main () {
  assert(sizeof(enumVar) == sizeof(unsigned long long));
  assert(sizeof(llMax) == sizeof(enumVar));
  assert(sizeof(intMax) == sizeof(int));
  assert(sizeof(intMax1) == sizeof(enumVar));
  return 0;
}
