export type ParsedTaskResponse = {

    title: string;

    description: string;

    priority: 1 | 2 | 3 | 4;

    dueDate: string | null;
};