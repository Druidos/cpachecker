// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2024 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

#define NULL (void*)0

struct list_node {
    int n;
    struct list_node *next;
};

int main()
{
    enum { len = 3 };

    int i;
    struct list_node *head;
    struct list_node *node;

    head = NULL;
    node = head;
    for (i = 1; i <= len; i++) {
        node = malloc(sizeof(struct list_node));
        node->n = i;
        node->next = head;
        head = node;
    }

    /* get third node */
    node = head->next->next;
    node->n = 24;

    while (head != NULL) {
        node = head;
        head = head->next;
        free(node);
    }

    return 0;
}
