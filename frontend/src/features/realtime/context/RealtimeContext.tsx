import { createContext, useContext } from "react";

import type { Client } from "@stomp/stompjs";

type RealtimeContextType = {
  client: Client | null;
};

export const RealtimeContext = createContext<RealtimeContextType>({
  client: null,
});

export const useRealtime = () => {
  return useContext(RealtimeContext);
};
