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

void *black_malloc(size_t size) {
  return 0;
}
void black_free(void *ptr) {
  if (!ptr) return;
  char *p = ptr;
  while((*--p = p)); // invalid deref, right?
}

void *(*alloc[COLOR_COUNT])(size_t size) = {
  [UNCOLORED] = malloc,
  [RED] = red_malloc,
  [GREEN] = green_malloc,
};
void (*dealloc[COLOR_COUNT])(void *ptr) = {
  [UNCOLORED] = free,
  [RED] = red_free,
  [GREEN] = green_free,
};

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

  enum color_t color = GREEN;
  new_head->data = green_malloc(sizeof(int));
  new_head->data[0] = __VERIFIER_undef_int();
}

void remove_head(list *old_head) {
  if (!*old_head) {
    return;
  }

  green_free((*old_head)->data);
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
