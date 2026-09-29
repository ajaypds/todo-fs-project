export type ParsedTaskResponse = {

    title: string;

    description: string;

    priority: 1 | 2 | 3 | 4;

    dueDate: string | null;
};

export type DecomposeTaskRequest = {
    title: string;
    description?: string;
};

export type DecomposedSubtaskItem = {
    title: string;
};

export type DecomposeTaskResponse = {
    subtasks: DecomposedSubtaskItem[];
};