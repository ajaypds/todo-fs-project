import { apiClient } from "../../../api/client";


export const getActivities = async () => {

    const response = await apiClient.get("/activities");

    return response.data;
};