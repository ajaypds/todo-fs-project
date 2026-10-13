import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getTasks,
  createTask,
  updateTask,
  deleteTask,
  reorderTasks,
  createSubtask,
  updateSubtask,
  deleteSubtask,
} from "./taskApi";
import type { Task, Subtask } from "../types/taskTypes";
import type { PageResponse } from "../../../types/pagination";
import toast from "react-hot-toast";

export const useTasks = () => {
  return useQuery({
    queryKey: ["tasks"],
    queryFn: getTasks,
  });
};

type CreateTaskContext = {
  previousTasks: PageResponse<Task> | undefined;
  optimisticId: string;
};

export const useCreateTask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createTask,

    onMutate: async (newTask) => {
      await queryClient.cancelQueries({ queryKey: ["tasks"] });
      const previousTasks = queryClient.getQueryData<PageResponse<Task>>(["tasks"]);
      const optimisticId = crypto.randomUUID();

      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;

          const optimisticTask: Task = {
            id: optimisticId,
            title: newTask.title,
            description: newTask.description,
            priority: newTask.priority ?? 1,
            completed: false,
            dueDate: newTask.dueDate,
            projectId: newTask.projectId || "",
            recurrenceType: newTask.recurrenceType ?? "NONE",
            recurrenceInterval: newTask.recurrenceInterval ?? 1,
            optimistic: true,
            subtasks: [],
            labels: [],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          };

          return {
            ...old,
            content: [...old.content, optimisticTask],
          };
        }
      );

      return { previousTasks, optimisticId };
    },

    onSuccess: (newSavedTask, _variables, context: CreateTaskContext | undefined) => {
      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.map((task) =>
              task.id === context?.optimisticId ? newSavedTask : task
            ),
          };
        }
      );
    },

    onError: (_error, _newTask, context: CreateTaskContext | undefined) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(["tasks"], context.previousTasks);
      }
      toast.error("Failed to create task. Changes reverted.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};

type TaskMutationContext = {
  previousTasks: PageResponse<Task> | undefined;
};

export const useUpdateTask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      taskId,
      payload,
    }: {
      taskId: string;
      payload: Record<string, unknown>;
    }) => updateTask(taskId, payload),

    onMutate: async ({ taskId, payload }) => {
      await queryClient.cancelQueries({ queryKey: ["tasks"] });
      const previousTasks = queryClient.getQueryData<PageResponse<Task>>(["tasks"]);

      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.map((task: Task) => {
              if (task.id === taskId) {
                return {
                  ...task,
                  ...payload,
                  optimistic: true,
                };
              }
              return task;
            }),
          };
        }
      );

      return { previousTasks };
    },

    onError: (_error, _variables, context: TaskMutationContext | undefined) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(["tasks"], context.previousTasks);
      }
      toast.error("Failed to update task. Reverted to previous state.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};

export const useDeleteTask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteTask,

    onMutate: async (taskId: string) => {
      await queryClient.cancelQueries({ queryKey: ["tasks"] });
      const previousTasks = queryClient.getQueryData<PageResponse<Task>>(["tasks"]);

      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.filter((task) => task.id !== taskId),
          };
        }
      );

      return { previousTasks };
    },

    onError: (_error, _taskId, context: TaskMutationContext | undefined) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(["tasks"], context.previousTasks);
      }
      toast.error("Failed to delete task. Restored.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};

export const useReorderTasks = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: reorderTasks,

    onMutate: async (taskIds: string[]) => {
      await queryClient.cancelQueries({ queryKey: ["tasks"] });
      const previousTasks = queryClient.getQueryData<PageResponse<Task>>(["tasks"]);

      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;
          const taskMap = new Map(old.content.map((t) => [t.id, t]));
          const reordered: Task[] = [];
          taskIds.forEach((id) => {
            const t = taskMap.get(id);
            if (t) {
              reordered.push(t);
              taskMap.delete(id);
            }
          });
          taskMap.forEach((t) => reordered.push(t));

          return {
            ...old,
            content: reordered,
          };
        }
      );

      return { previousTasks };
    },

    onError: (_error, _taskIds, context: TaskMutationContext | undefined) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(["tasks"], context.previousTasks);
      }
      toast.error("Failed to save task order. Reverted.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};

type CreateSubtaskContext = {
  previousTasks: PageResponse<Task> | undefined;
  optimisticSubtaskId: string;
};

export const useCreateSubtask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ taskId, title }: { taskId: string; title: string }) =>
      createSubtask(taskId, title),

    onMutate: async ({ taskId, title }) => {
      await queryClient.cancelQueries({ queryKey: ["tasks"] });
      const previousTasks = queryClient.getQueryData<PageResponse<Task>>(["tasks"]);
      const optimisticSubtaskId = crypto.randomUUID();

      const optimisticSubtask: Subtask = {
        id: optimisticSubtaskId,
        taskId,
        title,
        completed: false,
        position: 9999,
        createdAt: new Date().toISOString(),
      };

      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.map((task) => {
              if (task.id !== taskId) return task;
              const existingSubtasks = task.subtasks || [];
              return {
                ...task,
                subtasks: [...existingSubtasks, optimisticSubtask],
              };
            }),
          };
        }
      );

      return { previousTasks, optimisticSubtaskId };
    },

    onSuccess: (newSubtask: Subtask, { taskId }, context: CreateSubtaskContext | undefined) => {
      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.map((task) => {
              if (task.id !== taskId) return task;
              const subtasks = (task.subtasks || []).map((st) =>
                st.id === context?.optimisticSubtaskId ? newSubtask : st
              );
              return {
                ...task,
                subtasks,
              };
            }),
          };
        }
      );
    },

    onError: (_error, _variables, context: CreateSubtaskContext | undefined) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(["tasks"], context.previousTasks);
      }
      toast.error("Failed to add subtask. Reverted.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};

export const useUpdateSubtask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      taskId,
      subtaskId,
      payload,
    }: {
      taskId: string;
      subtaskId: string;
      payload: { title?: string; completed?: boolean; position?: number };
    }) => updateSubtask(taskId, subtaskId, payload),

    onMutate: async ({ taskId, subtaskId, payload }) => {
      await queryClient.cancelQueries({ queryKey: ["tasks"] });
      const previousTasks = queryClient.getQueryData<PageResponse<Task>>(["tasks"]);

      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.map((task) => {
              if (task.id !== taskId) return task;
              const updatedSubtasks = (task.subtasks || []).map((st) =>
                st.id === subtaskId ? { ...st, ...payload } : st
              );
              return {
                ...task,
                subtasks: updatedSubtasks,
              };
            }),
          };
        }
      );

      return { previousTasks };
    },

    onError: (_error, _variables, context: TaskMutationContext | undefined) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(["tasks"], context.previousTasks);
      }
      toast.error("Failed to update subtask. Reverted.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};

export const useDeleteSubtask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ taskId, subtaskId }: { taskId: string; subtaskId: string }) =>
      deleteSubtask(taskId, subtaskId),

    onMutate: async ({ taskId, subtaskId }) => {
      await queryClient.cancelQueries({ queryKey: ["tasks"] });
      const previousTasks = queryClient.getQueryData<PageResponse<Task>>(["tasks"]);

      queryClient.setQueryData<PageResponse<Task>>(
        ["tasks"],
        (old) => {
          if (!old) return old;
          return {
            ...old,
            content: old.content.map((task) => {
              if (task.id !== taskId) return task;
              return {
                ...task,
                subtasks: (task.subtasks || []).filter(
                  (st) => st.id !== subtaskId
                ),
              };
            }),
          };
        }
      );

      return { previousTasks };
    },

    onError: (_error, _variables, context: TaskMutationContext | undefined) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(["tasks"], context.previousTasks);
      }
      toast.error("Failed to delete subtask. Restored.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};