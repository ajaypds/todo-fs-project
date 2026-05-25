import { useEffect } from "react";
import { createStompClient } from "../../../realtime/socket";
import { usePresenceStore } from "../../../store/presenceStore";

export const usePresence = () => {

    const addUser = usePresenceStore((state) => state.addUser);

    useEffect(() => {

        const client = createStompClient();

        // client.activate();

        client.onConnect = () => {

            client.subscribe(

                "/topic/presence",

                (message) => {
                    const payload = JSON.parse(message.body);

                    if (payload.type === "USER_CONNECTED") {
                        addUser(payload.username);
                    }
                    console.log("Presence:", JSON.parse(message.body));
                }
            );
        };

        // return () => {
        //     client.deactivate();
        // };

    }, []);

};