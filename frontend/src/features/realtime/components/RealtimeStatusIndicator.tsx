import { useRealtime } from "../context/RealtimeContext";
import { RotateCw } from "lucide-react";
import { cn } from "../../../lib/cn";

type Props = {
  compact?: boolean;
  className?: string;
};

export const RealtimeStatusIndicator = ({ compact = false, className }: Props) => {
  const { status, reconnect } = useRealtime();

  if (status === "connected") {
    return (
      <div
        className={cn(
          "inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400 select-none",
          compact && "px-1.5 py-0.5",
          className
        )}
        title="Realtime WebSocket connected. All changes sync across devices instantly."
      >
        <span className="relative flex h-2 w-2 shrink-0">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500" />
        </span>
        {!compact && <span className="text-[11px] font-semibold tracking-wide">Live</span>}
      </div>
    );
  }

  if (status === "connecting") {
    return (
      <div
        className={cn(
          "inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-600 dark:text-amber-400 select-none",
          compact && "px-1.5 py-0.5",
          className
        )}
        title="Connecting to realtime server..."
      >
        <span className="relative flex h-2 w-2 shrink-0">
          <span className="animate-spin inline-flex rounded-full h-2 w-2 border border-amber-500 border-t-transparent" />
        </span>
        {!compact && (
          <span className="text-[11px] font-semibold tracking-wide">
            Connecting...
          </span>
        )}
      </div>
    );
  }

  // Disconnected
  return (
    <button
      type="button"
      onClick={reconnect}
      className={cn(
        "inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-600 dark:text-red-400 transition-colors cursor-pointer select-none group",
        compact && "px-1.5 py-0.5",
        className
      )}
      title="Realtime connection lost. Click to reconnect."
    >
      <span className="inline-flex rounded-full h-2 w-2 bg-red-500 shrink-0 group-hover:scale-110 transition-transform" />
      {!compact && (
        <span className="text-[11px] font-semibold tracking-wide">Offline</span>
      )}
      <RotateCw
        size={11}
        className="opacity-70 group-hover:opacity-100 group-hover:rotate-180 transition-all shrink-0"
      />
    </button>
  );
};
