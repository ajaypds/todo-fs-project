import { useEffect, useMemo } from "react";

import type { ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { RealtimeContext } from "../context/RealtimeContext";
import { usePresenceStore } from "../../../store/presenceStore";
import { createStompClient } from "../../../realtime/socket";
import type {
  ActivityRealtimeEvent,
  PresenceRealtimeEvent,
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

  const client = useMemo(() => {
    return createStompClient({
      onConnect: () => {
        console.log("Realtime connected");
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
    });
  }, [queryClient, addUser, removeUser, isAuthenticated]);

  useEffect(() => {
    console.log("Checking authentication for realtime client...");
    const token = authStorage.getAccessToken();
    if (!token) {
      console.log(
        "No access token found, skipping realtime client activation.",
      );
      return;
    }
    if (isAuthenticated) {
      console.log("Activating realtime client...");

      client.activate();
    } else {
      console.log("Deactivating realtime client...");
      client.deactivate();
    }
    return () => {
      client.deactivate();
    };
  }, [client, isAuthenticated]);

  return (
    <RealtimeContext.Provider
      value={{
        client,
      }}
    >
      {children}
    </RealtimeContext.Provider>
  );
};
