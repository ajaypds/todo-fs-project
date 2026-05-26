import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { authStorage } from "../features/auth/authStorage";

type CreateClientOptions = {
    onConnect?: () => void;
};

export const createStompClient = (
    options?: CreateClientOptions
) => {

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
        onConnect: options?.onConnect,
    });
};