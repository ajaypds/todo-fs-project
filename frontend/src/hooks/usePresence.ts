import { useEffect } from "react";
import { stompClient } from "../realtime/socket";

export const usePresence = () => {

    useEffect(() => {

        stompClient.onConnect = () => {

            stompClient.subscribe(

                "/topic/presence",

                (message) => {
                    console.log("Presence:", JSON.parse(message.body));
                }
            );
        };

    }, []);
};