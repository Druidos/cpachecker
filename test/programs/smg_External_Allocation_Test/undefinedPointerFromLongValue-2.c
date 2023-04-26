// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

struct Recursive {
  int s;
  struct Recursive *p;
  long q;
};


int foo() {
  struct Recursive *a;
  struct Recursive *b;
  a = ext_allocation();
  a->p->s = 10;
  b = (struct Recursive *) (a->q);

  // Not a pointer
  if (a->q == 1) {
    b->p->q = 1;
    free(b->p->p);
  }
  return 0;
}

int main() {
  foo();
  return 0;
}

