import { apiClient } from "../../../api/client";
import type { Activity } from "./activityTypes";


export const getActivities = async (): Promise<Activity[]> => {

    const response = await apiClient.get("/activities");

    return response.data;
};