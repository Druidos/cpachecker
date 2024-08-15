#define NULL (void *)0

struct item {
    int n;
    struct item *next;
    struct item *sub_list;
};

void list_init(struct item **node)
{
    *node = NULL;
}

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
    enum { list_size = 4 };

    int sub_list_sizes[] = { 1, 2 };
    int sub_smg_cycle = sizeof(sub_list_sizes) / sizeof(int);

    struct item *first, *tmp;
    int i;

    first = NULL;
    for (i = 1; i <= list_size; i++)
        list_add(i, &first);

    tmp = first;
    for (i = 1; i <= list_size; i++) {
        int j, cnt;
        cnt = sub_list_sizes[i % sub_smg_cycle];
        for (j = 1; j <= cnt; j++)
            list_add(i, &tmp->sub_list);
        tmp = tmp->next;
    }

    list_destroy(first);

    return 0;
}
