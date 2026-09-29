import { useEffect, useMemo, useState, useCallback } from "react";

import type { ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { RealtimeContext } from "../context/RealtimeContext";
import { usePresenceStore } from "../../../store/presenceStore";
import { createStompClient } from "../../../realtime/socket";
import type {
  ActivityRealtimeEvent,
  PresenceRealtimeEvent,
  RealtimeConnectionStatus,
  TaskRealtimeEvent,
} from "../types/realtimeTypes";
import { authStorage } from "../../auth/authStorage";
import { useAuthStore } from "../../../store/authStore";

type Props = {
  children: ReactNode;
};

export const RealtimeProvider = ({ children }: Props) => {
  const queryClient = useQueryClient();

  const addUser = usePresenceStore((state) => state.addUser);
  const removeUser = usePresenceStore((state) => state.removeUser);
  const isAuthenticated = useAuthStore((state) => !!state.token);

  const [status, setStatus] = useState<RealtimeConnectionStatus>("disconnected");

  const client = useMemo(() => {
    return createStompClient({
      beforeConnect: () => {
        setStatus("connecting");
      },
      onConnect: () => {
        console.log("Realtime connected");
        setStatus("connected");
        if (isAuthenticated) {
          console.log(
            "User is authenticated, subscribing to realtime topics...",
          );
        }
        queryClient.invalidateQueries({
          queryKey: ["presence"],
        });

        // TASKS

        client.subscribe(
          "/topic/tasks",

          (message) => {
            const payload: TaskRealtimeEvent = JSON.parse(message.body);

            console.log("Task event:", payload);

            queryClient.invalidateQueries({
              queryKey: ["tasks"],
            });
          },
        );

        // PRESENCE

        client.subscribe(
          "/topic/presence",

          (message) => {
            const payload: PresenceRealtimeEvent = JSON.parse(message.body);

            console.log("Presence event:", payload);

            if (payload.type === "USER_CONNECTED") {
              addUser(payload.username);
            }

            if (payload.type === "USER_DISCONNECTED") {
              removeUser(payload.username);
            }
          },
        );

        // ACTIVITIES

        client.subscribe(
          "/topic/activities",

          (message) => {
            const payload: ActivityRealtimeEvent = JSON.parse(message.body);

            console.log("Activity event:", payload);

            queryClient.invalidateQueries({
              queryKey: ["activities"],
            });
          },
        );
      },
      onDisconnect: () => {
        console.log("Realtime disconnected");
        setStatus("disconnected");
      },
      onWebSocketClose: () => {
        console.log("Realtime websocket closed");
        if (client.active) {
          setStatus("connecting");
        } else {
          setStatus("disconnected");
        }
      },
      onStompError: (frame) => {
        console.error("STOMP error:", frame);
        setStatus("disconnected");
      },
      onWebSocketError: (event) => {
        console.error("WebSocket error:", event);
        if (client.active) {
          setStatus("connecting");
        } else {
          setStatus("disconnected");
        }
      },
    });
  }, [queryClient, addUser, removeUser, isAuthenticated]);

  const reconnect = useCallback(() => {
    const token = authStorage.getAccessToken();
    if (!token || !isAuthenticated) return;
    setStatus("connecting");
    if (client.active) {
      client.deactivate().then(() => {
        client.activate();
      });
    } else {
      client.activate();
    }
  }, [client, isAuthenticated]);

  useEffect(() => {
    console.log("Checking authentication for realtime client...");
    const token = authStorage.getAccessToken();
    if (!token) {
      console.log(
        "No access token found, skipping realtime client activation.",
      );
      setStatus("disconnected");
      return;
    }
    if (isAuthenticated) {
      console.log("Activating realtime client...");
      setStatus("connecting");
      client.activate();
    } else {
      console.log("Deactivating realtime client...");
      setStatus("disconnected");
      client.deactivate();
    }
    return () => {
      setStatus("disconnected");
      client.deactivate();
    };
  }, [client, isAuthenticated]);

  return (
    <RealtimeContext.Provider
      value={{
        client,
        status,
        reconnect,
      }}
    >
      {children}
    </RealtimeContext.Provider>
  );
};
