import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getTasks, createTask, updateTask, deleteTask, reorderTasks } from "./taskApi";
import type { Task } from "../types/taskTypes";

import type { PageResponse } from "../../../types/pagination";


export const useTasks = () => {
    return useQuery({
        queryKey: ["tasks"],
        queryFn: getTasks,
    });
};

type Context = {
    previousTasks: PageResponse<Task> | undefined;
    optimisticId: string;
};

export const useCreateTask = () => {

    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: createTask,

        onSuccess: (newSavedTask, _variables, context: Context) => {
            // queryClient.invalidateQueries({
            //     queryKey: ["tasks"],
            // });
            console.log(context?.optimisticId);
            queryClient.setQueryData(
                ["tasks"],
                (old: PageResponse<Task> | undefined) => {
                    if (!old) {
                        return old;
                    }

                    console.log("Replacing temp task with saved task:", newSavedTask)

                    return {
                        ...old,
                        content: old.content.map((task) => {
                            return task.id === context?.optimisticId ? newSavedTask : task;
                        })
                    }
                }
            )
        },


        onMutate: async (
            newTask
        ) => {

            await queryClient.cancelQueries({

                queryKey: ["tasks"],
            });

            // const previousTasks = queryClient.getQueryData(["tasks"]);
            const previousTasks = queryClient.getQueryData<PageResponse<Task>>(["tasks"]);
            const optimisticId = crypto.randomUUID();

            queryClient.setQueryData(

                ["tasks"],

                (old: PageResponse<Task> | undefined) => {

                    if (!old) {
                        return old;
                    }

                    const mutatedTask = { ...newTask, id: optimisticId, completed: false, optimistic: true, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
                    console.log("mutating task: ", mutatedTask)

                    return {

                        ...old,

                        content: [

                            {
                                // id: optimisticId,

                                // title:
                                //     newTask.title,

                                // description:
                                //     newTask.description,

                                // completed: false,

                                // optimistic: true,
                                ...mutatedTask
                            },

                            ...old.content,
                        ],
                    };
                }
            );

            return {
                previousTasks, optimisticId
            };
        },

        onError: (
            error,
            newTask,
            context
        ) => {
            queryClient.setQueryData(
                ["tasks"],
                context?.previousTasks
            )
        },

        // onSettled: () => {

        //     queryClient.invalidateQueries({

        //         queryKey: ["tasks"],
        //     });
        // },
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
                            (task: Task) => {
                                task.optimistic = true;
                                return task.id === taskId ? { ...task, ...payload } : task
                            }
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

    const queryClient = useQueryClient();

    return useMutation({

        mutationFn: reorderTasks,

        onSuccess: () => {

            queryClient.invalidateQueries({
                queryKey: ["tasks"],
            });
        },
    });
};