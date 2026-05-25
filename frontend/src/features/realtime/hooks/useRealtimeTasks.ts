import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createStompClient } from "../../../realtime/socket";

export const useRealtimeTasks = () => {

    const queryClient = useQueryClient();


    useEffect(() => {
        const client = createStompClient();

        client.activate();

        client.onConnect = () => {

            client.subscribe(

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
            client.deactivate();
        };

    }, [queryClient]);
};