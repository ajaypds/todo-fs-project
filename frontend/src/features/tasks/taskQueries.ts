import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getTasks, createTask, updateTask, deleteTask } from "./taskApi";


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

        onSuccess: () => {
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