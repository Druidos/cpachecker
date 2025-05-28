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

typedef struct node *list;

struct node {
  list next;
  int color;
};

void add_head(list *old_head) {
  list new_head = 0;
  int color = __VERIFIER_undef_int();
  if (color == 1) {
    new_head = green_malloc(sizeof(struct node));
  } else if (color == 2) {
    new_head = red_malloc(sizeof(struct node));
  } else {
    color = 3;
    new_head = malloc(sizeof(struct node));
  }
  if (!new_head) {
    return;
  }

  new_head->next = *old_head;
  new_head->color = color;
  *old_head = new_head;
}

void remove_head(list *old_head) {
  if (!*old_head) {
    return;
  }
  list new_head = (*old_head)->next;
  switch ((*old_head)->color) {
  case 1: green_free(*old_head); break;
  case 2: red_free(*old_head); break;
  default: free(*old_head); break;
  }
  *old_head = new_head;
}

int main()
{
  list garland = 0;
  while (__VERIFIER_undef_int()) {
    add_head(&garland);
  }
  while (garland) {
    remove_head(&garland);
  }
  return 0;
}
