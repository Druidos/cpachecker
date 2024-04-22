#define NULL (void *)0

struct item {
    int n;
    struct item *next;
    struct item *sub_list;
};

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
    struct item *tmp;
    int i;

    first = NULL;
    list_add(1, &first);
    list_add(2, &first);
    list_add(21, &first->sub_list);

    while (first) {
        tmp = first;
        first = first->next;
        free(tmp->sub_list);
        free(tmp);
    }

    return 0;
}
