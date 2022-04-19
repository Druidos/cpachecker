// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0


int *malloc(size_t size);
void free(void *ptr);

int main() {
  int res, base;
  void *r = malloc(base);	
  if (-1000 < (char)(long)(res)) {
    free(r);
  } else {
    r = malloc(base);
  }
  
  r = malloc(base);
  
  if (200 > (char)(long)(res)) {
    free(r);
  } else {
    r = malloc(base);
  }
  
  return 0;
      
}
