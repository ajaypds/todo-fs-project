import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createStompClient } from "../../../realtime/socket";

export const useRealtimeActivities = () => {

    const queryClient = useQueryClient();

    useEffect(() => {

        const client = createStompClient();

        client.onConnect = () => {

            client.subscribe("/topic/activities",

                (message) => {

                    const payload = JSON.parse(message.body);

                    console.log("Realtime activity:", payload);

                    queryClient.invalidateQueries({

                        queryKey: [
                            "activities",
                        ],
                    });
                }
            );
        };

        client.activate();

        return () => {
            client.deactivate();
        };

    }, [queryClient]);
};