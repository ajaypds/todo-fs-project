import { apiClient } from "../../../api/client";
import type { Task } from "../types/taskTypes";

import type { PageResponse } from "../../../types/pagination";


export type CreateTaskRequest = {
    title: string;
    description?: string;
    priority?: number;
    dueDate?: string;
};

export const getTasks = async (): Promise<
    PageResponse<Task>
> => {
    const response = await apiClient.get("/tasks");

    return response.data;
};

export const createTask = async (request: CreateTaskRequest) => {

    const response = await apiClient.post("/tasks", request);

    return response.data;
};

export const updateTask = async (taskId: string, payload: Record<string, unknown>) => {

    const response = await apiClient.put(`/tasks/${taskId}`, payload);

    return response.data;
};

export const deleteTask = async (taskId: string) => {
    await apiClient.delete(`/tasks/${taskId}`);
};

export const reorderTasks = async (taskIds: string[]) => {

    await apiClient.post(
        "/tasks/reorder",
        {
            taskIds,
        }
    );
};