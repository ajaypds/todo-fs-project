import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getTasks, createTask, updateTask, deleteTask, reorderTasks } from "./taskApi";
import type { Task } from "./taskTypes";

import type { PageResponse } from "../../types/pagination";


export const useTasks = () => {
    return useQuery({
        queryKey: ["tasks"],
        queryFn: getTasks,
    });
};

export const useCreateTask = () => {

    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: createTask,

        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ["tasks"],
            });
        },
    });
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

        onMutate: async ({
            taskId,
            payload,
        }) => {

            await queryClient.cancelQueries({
                queryKey: ["tasks"],
            });

            const previousTasks =
                queryClient.getQueryData([
                    "tasks",
                ]);

            queryClient.setQueryData(
                ["tasks"],
                (old: PageResponse<Task> | undefined) => {

                    if (!old) {
                        return old;
                    }

                    return {
                        ...old,

                        content: old.content.map(
                            (task: Task) =>

                                task.id === taskId
                                    ? {
                                        ...task,
                                        ...payload,
                                    }
                                    : task
                        ),
                    };
                }
            );

            return {
                previousTasks,
            };
        },

        onError: (
            _error,
            _variables,
            context
        ) => {

            queryClient.setQueryData(
                ["tasks"],
                context?.previousTasks
            );
        },

        onSettled: () => {

            queryClient.invalidateQueries({
                queryKey: ["tasks"],
            });
        },
    });
};

export const useDeleteTask = () => {

    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: deleteTask,

        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ["tasks"],
            });
        },
    });
};

export const useReorderTasks = () => {

    const queryClient =
        useQueryClient();

    return useMutation({

        mutationFn: reorderTasks,

        onSuccess: () => {

            queryClient.invalidateQueries({
                queryKey: ["tasks"],
            });
        },
    });
};