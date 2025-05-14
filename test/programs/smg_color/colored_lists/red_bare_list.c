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
};

void add_head(list *old_head) {
  list new_head = red_malloc(sizeof(struct node));
  if (!new_head) {
    return;
  }

  new_head->next = *old_head;
  *old_head = new_head;
}

void remove_head(list *old_head) {
  if (!*old_head) {
    return;
  }
  list new_head = (*old_head)->next;
  red_free(*old_head);
  *old_head = new_head;
}

int main()
{
  list red = 0;
  while (__VERIFIER_undef_int()) {
    add_head(&red);
  }
  while (red) {
    remove_head(&red);
  }
  return 0;
}
