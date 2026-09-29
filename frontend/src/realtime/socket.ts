import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { authStorage } from "../features/auth/authStorage";

type CreateClientOptions = {
    beforeConnect?: () => void;
    onConnect?: () => void;
    onDisconnect?: () => void;
    onWebSocketClose?: (event: unknown) => void;
    onStompError?: (frame: unknown) => void;
    onWebSocketError?: (event: unknown) => void;
};

export const createStompClient = (
    options?: CreateClientOptions
) => {
    const client = new Client({
        webSocketFactory: () =>
            new SockJS(
                `${import.meta.env.VITE_API_BASE_URL.replace("/api/v1", "")}/ws`
            ),
        reconnectDelay: 5000,
        beforeConnect: () => {
            client.connectHeaders = {
                Authorization: `Bearer ${authStorage.getAccessToken()}`,
            };
            options?.beforeConnect?.();
        },
        onConnect: options?.onConnect,
        onDisconnect: options?.onDisconnect,
        onWebSocketClose: options?.onWebSocketClose,
        onStompError: options?.onStompError,
        onWebSocketError: options?.onWebSocketError,
    });

    return client;
};