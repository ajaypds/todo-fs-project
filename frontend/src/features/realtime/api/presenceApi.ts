import { apiClient } from "../../../api/client";
import type { UserPresenceResponse } from "../types/realtimeTypes";

export const getPresenceStatus = async (): Promise<UserPresenceResponse> => {
    const response = await apiClient.get<UserPresenceResponse>("/presence");
    return response.data;
};

export const getOnlineUsers = getPresenceStatus;