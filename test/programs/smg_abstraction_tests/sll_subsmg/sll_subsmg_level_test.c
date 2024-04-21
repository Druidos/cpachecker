#define NULL (void *)0

struct item {
    int n;
    struct item *next;
    struct item *sub_list;
};

void sublist_destroy(struct item *node)
{
    struct item *tmp;

    while (node) {
        tmp = node;
        node = node->next;
        free(tmp);
    }
}

void list_destroy(struct item *node)
{
    struct item *tmp;

    while (node) {
        tmp = node;
        sublist_destroy(node->sub_list);
        node = node->next;
        free(tmp);
    }
}

void list_add(int n, struct item **lst)
{
    struct item *tmp;
    tmp = malloc(sizeof(struct item));
    tmp->n = n;
    tmp->next = *lst;
    tmp->sub_list = NULL;
    *lst = tmp;
}

int main()
{
    struct item *first;

    first = NULL;
    list_add(1, &first);
    list_add(2, &first);
    list_add(21, &first->sub_list);

    list_destroy(first);

    return 0;
}
