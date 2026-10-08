import { useQuery } from "@tanstack/react-query";
import { getPresenceStatus } from "./presenceApi";
import type { UserPresenceResponse } from "../types/realtimeTypes";

export const useUserPresence = () => {
    return useQuery<UserPresenceResponse>({
        queryKey: ["presence"],
        queryFn: getPresenceStatus,
    });
};

export const useOnlineUsers = useUserPresence;