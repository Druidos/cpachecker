// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2025 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

typedef unsigned long size_t;

enum color_t {
  UNCOLORED,
  RED,
  GREEN,
  COLOR_COUNT,
};

void *red_malloc(size_t size);
void red_free(void *ptr);

void *green_malloc(size_t size);
void green_free(void *ptr);

extern void *malloc(size_t size);
extern void free(void *ptr);

typedef struct node *list;

struct node {
  list next;
  int *data;
  enum color_t color;
};

// festoon is (colored) lamp bulbs on a cable
// so this list is uncolored nodes with colored regions in data

void add_head(list *old_head) {
  list new_head = malloc(sizeof(struct node));
  if (!new_head) {
    return;
  }

  new_head->next = *old_head;
  *old_head = new_head;

  new_head->color = __VERIFIER_undef_int();
  switch (new_head->color) {
  case RED:
    new_head->data = red_malloc(sizeof(int));
    break;
  case GREEN:
    new_head->data = green_malloc(sizeof(int));
    break;
  default:
    new_head->data = malloc(sizeof(int));
    new_head->color = UNCOLORED;
    break;
  }
}

void remove_head(list *old_head) {
  if (!*old_head) {
    return;
  }

  switch ((*old_head)->color) {
  case RED:
    red_free((*old_head)->data);
    break;
  case GREEN:
    green_free((*old_head)->data);
    break;
  default:
    free((*old_head)->data);
    break;
  }
  list new_head = (*old_head)->next;
  free(*old_head);
  *old_head = new_head;
}

int main()
{
  list festoon = 0;
  while (__VERIFIER_undef_int()) {
    add_head(&festoon);
  }
  while (festoon) {
    remove_head(&festoon);
  }
  return 0;
}
