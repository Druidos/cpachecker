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
  list prev;
  list next;
  void *data;
};

void add_head(list *old_head) {
  list new_head = green_malloc(sizeof(struct node));
  if (!new_head) {
    return;
  }

  if (!*old_head) {
    new_head->next = new_head;
    new_head->prev = new_head;
  } else {
    new_head->next = *old_head;
    new_head->prev = (*old_head)->prev;
    new_head->prev->next = new_head;
    new_head->next->prev = new_head;
  }
  *old_head = new_head;

  new_head->data = malloc(sizeof(int));
  while (!new_head->data) {
    new_head->data = malloc(sizeof(int));
  }
  *(new_head->data) = __VERIFIER_undef_int();
  __VERIFIER_BUILTIN_PLOT("ret_add_head");
}

void remove_head(list *old_head) {
  if (!*old_head) {
    return;
  }
  list head = (*old_head);
  head->next->prev = head->prev;
  head->prev->next = head->next;
  head = head->next;

  free((*old_head)->data);
  green_free(*old_head);

  if (*old_head == head) {
    *old_head = 0;
  } else {
    *old_head = head;
  }
}

// doubly linked cyclic list

int main()
{
  list wreath = 0;
  while (__VERIFIER_undef_int()) {
    add_head(&wreath);
  }
  while (wreath) {
    remove_head(&wreath);
  }
  return 0;
}
