import { useQuery } from "@tanstack/react-query";
import { getOnlineUsers } from "./presenceApi";

export const useOnlineUsers = () => {

    return useQuery({

        queryKey: ["presence"],

        queryFn: getOnlineUsers,
    });
};