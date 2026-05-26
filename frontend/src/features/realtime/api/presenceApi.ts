import { apiClient } from "../../../api/client";

export const getOnlineUsers = async (): Promise<string[]> => {

    const response = await apiClient.get("/presence");

    return response.data;
};