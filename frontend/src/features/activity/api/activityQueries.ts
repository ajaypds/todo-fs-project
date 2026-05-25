import { useQuery } from "@tanstack/react-query";
import { getActivities } from "./activityApi";

export const useActivities = () => {

    return useQuery({
        queryKey: ["activities"],
        queryFn: getActivities,
    });
};