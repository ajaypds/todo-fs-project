import { apiClient } from "../../../api/client";
import type { TaskComment } from "../types/commentTypes";
import type { Activity } from "../../activity/api/activityTypes";

export const getTaskComments = async (taskId: string): Promise<TaskComment[]> => {
  const response = await apiClient.get<TaskComment[]>(`/tasks/${taskId}/comments`);
  return response.data;
};

export const createTaskComment = async (
  taskId: string,
  content: string
): Promise<TaskComment> => {
  const response = await apiClient.post<TaskComment>(`/tasks/${taskId}/comments`, {
    content,
  });
  return response.data;
};

export const updateTaskComment = async (
  taskId: string,
  commentId: string,
  content: string
): Promise<TaskComment> => {
  const response = await apiClient.put<TaskComment>(
    `/tasks/${taskId}/comments/${commentId}`,
    { content }
  );
  return response.data;
};

export const deleteTaskComment = async (
  taskId: string,
  commentId: string
): Promise<void> => {
  await apiClient.delete(`/tasks/${taskId}/comments/${commentId}`);
};

export const getTaskActivities = async (taskId: string): Promise<Activity[]> => {
  const response = await apiClient.get<Activity[]>(`/tasks/${taskId}/activities`);
  return response.data;
};
