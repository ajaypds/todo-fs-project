import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { stompClient } from "../../../realtime/socket";

export const useRealtimeTasks = () => {

    const queryClient = useQueryClient();

    useEffect(() => {

        stompClient.activate();

        stompClient.onConnect = () => {

            stompClient.subscribe(

                "/topic/tasks",

                (message) => {

                    const payload = JSON.parse(message.body);

                    console.log("Realtime event:", payload);

                    queryClient.invalidateQueries({
                        queryKey: ["tasks"],
                    });
                }
            );
        };

        return () => {
            stompClient.deactivate();
        };

    }, [queryClient]);
};