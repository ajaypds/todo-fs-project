import { useEffect } from "react";
import { usePresenceStore } from "../../../store/presenceStore";

export const OnlineUsers = () => {
  const users = usePresenceStore((state) => state.users);

  useEffect(() => {
    // console.log("Online users updated:", users);
  }, [users]);

  return (
    <div className="flex items-center gap-2 text-sm text-muted">
      <div className="w-2 h-2 rounded-full bg-green-500" />
      {users.length} online
    </div>
  );
};
