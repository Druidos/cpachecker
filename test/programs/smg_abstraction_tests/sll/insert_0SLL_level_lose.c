// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2024 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

#define NULL (void *)0

struct item_data {
    struct item_data *next;
    long data;
    int n;
};

struct item {
    struct item_data *data;
    struct item *next;
    int n;
};

void list_add(int n, struct item **lst)
{
    struct item *tmp;
    tmp = malloc(sizeof(struct item));
    tmp->next = *lst;
    tmp->data = NULL;
    tmp->n = n;
    *lst = tmp;
}

int main()
{
    struct item *first, *tmp;
    struct item_data *data;

    first = NULL;
    list_add(1, &first);
    list_add(3, &first);
    list_add(2, &first);

    /* add first sub list node */
    first->n = 2;
    first->data = malloc(sizeof(struct item_data));
    first->data->next = NULL;
    first->data->n = 2;
    first->data->data = 0;

    /* add second sub list node */
    first->data->next = malloc(sizeof(struct item_data));
    first->data->next->next = NULL;
    first->data->next->n = 2;
    first->data->next->data = 0;

    /* list free */
    while (first) {
        while (first->data) {
            struct item_data *tmp_data;
            tmp_data = first->data;
            first->data = tmp_data->next;
            free(tmp_data);
        }
        tmp = first;
        first = first->next;
        free(tmp);
    }

    return 0;
}
