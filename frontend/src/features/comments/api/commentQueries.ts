import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getTaskComments,
  createTaskComment,
  updateTaskComment,
  deleteTaskComment,
  getTaskActivities,
} from "./commentApi";
import type { TaskComment } from "../types/commentTypes";

export const useTaskComments = (taskId?: string) => {
  return useQuery({
    queryKey: ["tasks", taskId, "comments"],
    queryFn: () => getTaskComments(taskId!),
    enabled: !!taskId,
  });
};

export const useTaskActivities = (taskId?: string) => {
  return useQuery({
    queryKey: ["tasks", taskId, "activities"],
    queryFn: () => getTaskActivities(taskId!),
    enabled: !!taskId,
  });
};

export const useCreateTaskComment = (taskId?: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (content: string) => createTaskComment(taskId!, content),
    onSuccess: (newComment) => {
      queryClient.setQueryData<TaskComment[]>(
        ["tasks", taskId, "comments"],
        (old = []) => [...old, newComment]
      );
      queryClient.invalidateQueries({ queryKey: ["tasks", taskId, "activities"] });
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      queryClient.invalidateQueries({ queryKey: ["activities"] });
    },
  });
};

export const useUpdateTaskComment = (taskId?: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ commentId, content }: { commentId: string; content: string }) =>
      updateTaskComment(taskId!, commentId, content),
    onSuccess: (updatedComment) => {
      queryClient.setQueryData<TaskComment[]>(
        ["tasks", taskId, "comments"],
        (old = []) =>
          old.map((c) => (c.id === updatedComment.id ? updatedComment : c))
      );
    },
  });
};

export const useDeleteTaskComment = (taskId?: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (commentId: string) => deleteTaskComment(taskId!, commentId),
    onSuccess: (_, commentId) => {
      queryClient.setQueryData<TaskComment[]>(
        ["tasks", taskId, "comments"],
        (old = []) => old.filter((c) => c.id !== commentId)
      );
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};
