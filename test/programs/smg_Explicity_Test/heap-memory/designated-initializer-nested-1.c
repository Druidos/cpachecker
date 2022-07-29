// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

typedef unsigned long size_t;
static void *memcpy(void *to, void *from, size_t len);

void ldv_unexpected_error(void)
{
  char tmp = *((char *)0);
  return;
}

struct ldv_struct1 {
   int field1 ;
   int field2 ;
};

struct ldv_struct2 {
   int field1;
   struct ldv_struct1 field2 ;
   int field3;
};

struct ldv_struct2 ldv_var = {.field1 = 1, .field2 = {.field1 = 2, .field2 = 3}, .field3 = 4};

int main() {
  struct ldv_struct2 var;
  if (((ldv_var.field1 != 1 || ldv_var.field2.field1 != 2) || ldv_var.field2.field2 != 3) || ldv_var.field3 != 4)
    ldv_unexpected_error();
  memcpy((void *)(& var),(void *)(& ldv_var),16UL);
  if (((var.field1 != 1 || var.field2.field1 != 2) || var.field2.field2 != 3) || var.field3 != 4)
    ldv_unexpected_error();
  return 0;
}
