// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

typedef unsigned long __kernel_ulong_t;
typedef __kernel_ulong_t __kernel_size_t;
typedef __kernel_size_t size_t;

void *malloc(size_t size);
void free(void *ptr);

int main() {
  long res;
  void *r = malloc(2);
  if (1000UL > (long)(char)(res)) {
    free(r);
  } else {
    //unreached path
    //утечка памяти
    r = malloc(2);
  }
}

//когда слева тоже есть приведение типа, то работает верно
//if ((unsigned short)1000UL > (unsigned int)(res)) 
