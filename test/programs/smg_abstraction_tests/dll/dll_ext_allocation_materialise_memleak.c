// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2024 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

#include "../ext_allocation_abstraction_header.h"

struct list_node {
    struct list_node *next;
    struct list_node *prev;
};

int main()
{
    int i;
    struct list_node *head;
    struct list_node *node;
    struct list_node *prev_node;

    head = ext_allocation();
    head->prev = NULL;
    node = head;

    /* abstraction */
    for (i = 0; i < abstraction_sequence_length; i++) {
        prev_node = node;
        node = node->next;
        node->prev = prev_node;
    }

    /* materialise head node */
    node = head->next;
    
    /* separate the head from the body so that it is not marked as external*/
    node->prev = NULL;

    return 0;
}
