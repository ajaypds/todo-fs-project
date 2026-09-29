import { createContext, useContext } from "react";

import type { Client } from "@stomp/stompjs";
import type { RealtimeConnectionStatus } from "../types/realtimeTypes";

type RealtimeContextType = {
  client: Client | null;
  status: RealtimeConnectionStatus;
  reconnect: () => void;
};

export const RealtimeContext = createContext<RealtimeContextType>({
  client: null,
  status: "disconnected",
  reconnect: () => {},
});

export const useRealtime = () => {
  return useContext(RealtimeContext);
};
