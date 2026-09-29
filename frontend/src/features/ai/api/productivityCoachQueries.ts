import { useMutation } from "@tanstack/react-query";
import { getInsights } from "./productivityCoachApi";

export const useProductivityCoach = () => {

    return useMutation({
        mutationFn: getInsights,
    });
};