import { usePresenceStore } from "../../../store/presenceStore";
import { useRealtime } from "../../realtime/context/RealtimeContext";
import { Users } from "lucide-react";

export const OnlineUsers = () => {
  const users = usePresenceStore((state) => state.users);
  const { status } = useRealtime();

  if (status !== "connected") {
    return null;
  }

  return (
    <div className="flex items-center gap-1.5 text-xs text-muted">
      <Users size={13} className="text-muted" />
      <span>
        {users.length} {users.length === 1 ? "user" : "users"} online
      </span>
    </div>
  );
};
