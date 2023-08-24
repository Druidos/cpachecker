// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

#include <stdlib.h>

void check_free(char *p, long i) {
  if (i != 0) {
    free(p);
  }
}

int main()
{
    char *data;
    int i = 0;

    data = malloc(20);
    if (!data) {
      return 1;
    }

    for (i = 19; i>=0; i--) {
       check_free(data, data - &data[i]);
    }

    return 0;
}
