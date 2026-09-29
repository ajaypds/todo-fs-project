import { useMutation } from "@tanstack/react-query";
import { decomposeTask, parseTask } from "./aiApi";

export const useParseTask = () => {
    return useMutation({
        mutationFn: parseTask,
    });
};

export const useDecomposeTask = () => {
    return useMutation({
        mutationFn: decomposeTask,
    });
};