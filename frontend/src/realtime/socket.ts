import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { authStorage } from "../features/auth/authStorage";

export const createStompClient = () => {

    const socket = new SockJS(

        `${import.meta.env
            .VITE_API_BASE_URL
            .replace("/api/v1", "")}/ws`
    );

    return new Client({

        webSocketFactory: () => socket,
        reconnectDelay: 5000,
        connectHeaders: {
            Authorization: `Bearer ${authStorage.getAccessToken()}`,
        },
    });
};