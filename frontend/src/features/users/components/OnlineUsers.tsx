import { usePresenceStore } from "../../../store/presenceStore";
import { useRealtime } from "../../realtime/context/RealtimeContext";
import { Laptop } from "lucide-react";

export const OnlineUsers = () => {
  const { activeSessions } = usePresenceStore((state) => ({
    online: state.online,
    activeSessions: state.activeSessions,
  }));
  const { status } = useRealtime();

  if (status !== "connected") {
    return null;
  }

  return (
    <div
      className="flex items-center gap-1.5 text-xs text-muted"
      title={
        activeSessions > 1
          ? `${activeSessions} active browser sessions syncing in real-time`
          : "Workspace live & synchronized"
      }
    >
      <span className="relative flex h-2 w-2">
        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
        <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500" />
      </span>
      <Laptop size={13} className="text-muted" />
      <span>
        {activeSessions > 1
          ? `${activeSessions} sessions connected`
          : "Workspace synced"}
      </span>
    </div>
  );
};
