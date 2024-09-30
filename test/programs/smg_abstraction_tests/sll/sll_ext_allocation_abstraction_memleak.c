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
};

int main()
{
    int i;
    struct list_node *head;
    struct list_node *node;

    head = ext_allocation();
    node = head;

    /* abstraction */
    for (i = 0; i < abstraction_sequence_length; i++)
        node = node->next;

    return 0;
}
