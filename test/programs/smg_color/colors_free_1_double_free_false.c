// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2025 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

typedef unsigned long size_t;

void *red_malloc(size_t size);
void red_free(void *ptr);

void *green_malloc(size_t size);
void green_free(void *ptr);

void *malloc(size_t size);
void free(void *ptr);

struct ref_t {
  struct ref_t *ref;
  void *data;
  int size;
};

int main()
{
  struct ref_t *green, *red, *no_color;
  green = green_malloc(24);
  red = red_malloc(24);
  no_color = malloc(24);
  green->ref = no_color;
  no_color->ref = red;
  green_free(green);
  red_free(red);
  red_free(no_color->ref);
  free(no_color);
  return 0;
}
