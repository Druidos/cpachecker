// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0
int result;
extern int test(int a, int b);

int * sum(int a, int b) {
  result = a + b;
  return &result;
}

int * diff(int a, int b) {
  result = a - b;
  return &result;
}

int * mult(int a, int b) {
  result = a * b;
  return &result;
}

int * div(int a, int b) {
  result = a / b;
  return &result;
}

typedef int *(*function_type)(int , int );
function_type func_table[5] = {& sum, & diff, & div, & mult, 0};

int main() {
    int i;
    int * req;
    function_type func;

    i = 0;
    func = func_table[i];
    req = func(4, 0);
    if (*req != 4) {
      free(req);
    }
    req = (*(func_table[i]))(4, 0);
    if (*req != 4) {
      free(req);
    }

    i = 1;
    func = func_table[i];
    req = func(4, 1);
    if (*req != 3) {
      free(req);
    }
    req = (*(func_table[i]))(4, 1);
    if (*req != 3) {
      free(req);
    }

    i = 2;
    func = func_table[i];
    req = func(4, 2);
    if (*req != 2) {
      free(req);
    }
    req = (*(func_table[i]))(4, 2);
    if (*req != 2) {
      free(req);
    }

    i = 3;
    func = func_table[i];
    req = func(4, 3);
    if (*req != 12) {
      free(req);
    }
    req = (*(func_table[i]))(4, 3);
    if (*req != 12) {
      free(req);
    }

    result = test(1, 1);

    i = 5;
    req = (*(func_table[i]))(4, 4);
    if (*req != 0) {
      free(req);
    }
    return 0;
}
