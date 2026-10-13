import { useMutation } from "@tanstack/react-query";
import { decomposeTask, parseTask, generateDailyPlan } from "./aiApi";

export const useParseTask = () => {
    return useMutation({
        mutationFn: (input: string) => parseTask(input),
    });
};

export const useDecomposeTask = () => {
    return useMutation({
        mutationFn: decomposeTask,
    });
};

export const useDailyPlanner = () => {
    return useMutation({
        mutationFn: generateDailyPlan,
    });
};