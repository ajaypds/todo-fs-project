import SockJS from "sockjs-client";

import { Client } from "@stomp/stompjs";

const socket = new SockJS(
    `${import.meta.env
        .VITE_API_BASE_URL
        .replace("/api/v1", "")}/ws`
);

export const stompClient = new Client({

    webSocketFactory: () => socket,
    reconnectDelay: 5000,
});