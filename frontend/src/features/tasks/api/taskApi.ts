import { apiClient } from "../../../api/client";
import type { Task, Subtask, RecurrenceType } from "../types/taskTypes";
import type { PageResponse } from "../../../types/pagination";

export type CreateTaskRequest = {
  title: string;
  description?: string;
  priority?: number;
  dueDate?: string;
  projectId?: string;
  labelIds?: string[];
  recurrenceType?: RecurrenceType;
  recurrenceInterval?: number;
};

export const getTasks = async (): Promise<PageResponse<Task>> => {
  const response = await apiClient.get("/tasks");
  return response.data;
};

export const createTask = async (request: CreateTaskRequest): Promise<Task> => {
  const response = await apiClient.post("/tasks", request);
  return response.data;
};

export const updateTask = async (
  taskId: string,
  payload: Record<string, unknown>
): Promise<Task> => {
  const response = await apiClient.put(`/tasks/${taskId}`, payload);
  return response.data;
};

export const deleteTask = async (taskId: string): Promise<void> => {
  await apiClient.delete(`/tasks/${taskId}`);
};

export const reorderTasks = async (taskIds: string[]): Promise<void> => {
  await apiClient.post("/tasks/reorder", {
    taskIds,
  });
};

export const createSubtask = async (
  taskId: string,
  title: string
): Promise<Subtask> => {
  const response = await apiClient.post(`/tasks/${taskId}/subtasks`, { title });
  return response.data;
};

export const updateSubtask = async (
  taskId: string,
  subtaskId: string,
  payload: { title?: string; completed?: boolean; position?: number }
): Promise<Subtask> => {
  const response = await apiClient.patch(
    `/tasks/${taskId}/subtasks/${subtaskId}`,
    payload
  );
  return response.data;
};

export const deleteSubtask = async (
  taskId: string,
  subtaskId: string
): Promise<void> => {
  await apiClient.delete(`/tasks/${taskId}/subtasks/${subtaskId}`);
};