export type Task = {
    id: string;

    title: string;

    description?: string;

    completed: boolean;

    priority: number;

    dueDate?: string;

    createdAt: string;

    updatedAt: string;

    projectId: string;
};