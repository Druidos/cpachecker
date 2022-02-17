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

int *malloc(size_t size);
void free(void *ptr);
long rand();


int main() {
  long res;
  long num;
  res = 5L & num;
  int * r = malloc(20 * sizeof(int));
  r[res] = 10;
  free(r);
 }

/*
//работает некорректно
int main() {
  long res;
  long base;
  long buf = -14;
  if (base > 0)
  	res = base & buf;
  else 
  	res = 10;
  int * r = malloc(20 * sizeof(int));
  r[res] = 5;
  free(r);
 }
*/
/*
int main() {
  long res;
  long base;
  res = base & 14;
  int * r = malloc(13 * sizeof(int));
  if (res > base)
  {
  	r = malloc(1);
  }
  else
  {
  	if (res >= 0)
  		r[res] = 5;
  	free(r);
  }
}
*/

//Проблема с правой границей во-первых
//Проблема с malloc - почему из него получается 2

/*
int main() {
  long res, base;
  res = base & 3;
  void *r = malloc(base);
  r[res] = 5;	
  if (res > 10)
  {
	  if (1000UL > (char)(res)) {
	    free(r);
	  } else {
	    //unreached path
	    //утечка памяти
	    r = malloc();
	  }
  }
  else 
  	free(r);
  
  if (res < 10)
  	int k = 5;
}
*/
//когда слева тоже есть приведение типа, то работает верно
//if ((unsigned short)1000UL > (unsigned int)(res)) 
