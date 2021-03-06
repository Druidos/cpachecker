// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 11 "regression-tests/blast_incorrect.c"
typedef int rr;
#line 71 "/usr/include/assert.h"
extern void __assert_fail(char const   *__assertion , char const   *__file , unsigned int __line ,
                          char const   *__function ) ;
#line 13 "regression-tests/blast_incorrect.c"
rr yyyy  ;
#line 15 "regression-tests/blast_incorrect.c"
rr *getrr(void) 
{ rr *r ;

  {
#line 17
  r = & yyyy;
#line 18
  *r = 1;
#line 19
  return (r);
}
}
#line 22 "regression-tests/blast_incorrect.c"
int main(void) 
{ rr *ptr1 ;
  rr *ptr2 ;
  rr __cil_tmp3 ;
  rr __cil_tmp4 ;

  {
#line 25
  ptr1 = getrr();
#line 29
  ptr2 = ptr1;
  {
#line 30
  __cil_tmp3 = *ptr2;
#line 30
  if (__cil_tmp3 == 1) {

  } else {
#line 30
    __assert_fail("*ptr2 == 1", "regression-tests/blast_incorrect.c", 30U, "main");
  }
  }
#line 31
  *ptr2 = 2;
#line 33
  ptr2 = ptr1;
  {
#line 34
  __cil_tmp4 = *ptr2;
#line 34
  if (__cil_tmp4 == 1) {

  } else {
#line 34
    __assert_fail("*ptr2 == 1", "regression-tests/blast_incorrect.c", 34U, "main");
  }
  }
#line 35
  *ptr2 = 2;
#line 37
  return (0);
}
}
