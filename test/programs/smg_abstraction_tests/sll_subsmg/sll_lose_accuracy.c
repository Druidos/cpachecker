#define NULL (void *)0

struct item_data {
    void *data1;
    long data2;
};

struct item {
    struct item_data *data;
    struct item *next;
};

void list_add(struct item **lst)
{
    struct item *tmp;
    tmp = malloc(sizeof(struct item));
    tmp->next = *lst;
    tmp->data = NULL;
    *lst = tmp;
}

int main()
{
    struct item *first, *tmp;
    struct item_data *data;

    first = NULL;
    list_add(&first);
    first->data = malloc(sizeof(struct item_data));
    first->data->data1 = NULL;
    first->data->data2 = 0;

    list_add(&first);

    while (first) {
        /*
        we are not freeing up memory of first->data,
        but the verdict is true 
        */
        tmp = first;
        first = first->next;
        free(tmp);
    }

    return 0;
}
