import Drawer from "../../../components/ui/Drawer";
import { useActivities } from "../api/activityQueries";
import type { Activity } from "../api/activityTypes";
import {
  Activity as ActivityIcon,
  PlusCircle,
  CheckCircle2,
  Trash2,
  Edit3,
  Clock,
} from "lucide-react";
import { formatTimeAgo } from "../../../utils/date";

type Props = {
  open: boolean;
  onClose: () => void;
};

const getActivityIcon = (type: string, message: string) => {
  const lowerMsg = message.toLowerCase();
  if (type === "TASK_CREATED" || lowerMsg.includes("created")) {
    return <PlusCircle size={15} className="text-emerald-500 shrink-0 mt-0.5" />;
  }
  if (lowerMsg.includes("completed")) {
    return <CheckCircle2 size={15} className="text-blue-500 shrink-0 mt-0.5" />;
  }
  if (type === "TASK_DELETED" || lowerMsg.includes("deleted")) {
    return <Trash2 size={15} className="text-red-500 shrink-0 mt-0.5" />;
  }
  if (type === "TASK_UPDATED" || lowerMsg.includes("updated")) {
    return <Edit3 size={15} className="text-amber-500 shrink-0 mt-0.5" />;
  }
  return <Clock size={15} className="text-muted shrink-0 mt-0.5" />;
};

export const ActivityDrawer = ({ open, onClose }: Props) => {
  const { data: activities = [], isLoading } = useActivities();

  return (
    <Drawer open={open} onClose={onClose} title="Activity Feed">
      <div className="space-y-4">
        <p className="text-xs text-muted leading-relaxed">
          Real-time activity audit trail of changes made across your workspace.
        </p>

        {isLoading ? (
          <div className="py-8 text-center text-xs text-muted italic">
            Loading activities...
          </div>
        ) : activities.length === 0 ? (
          <div className="py-12 text-center text-muted space-y-2">
            <ActivityIcon size={32} className="mx-auto text-muted/40" />
            <p className="text-sm font-medium">No activity recorded yet</p>
            <p className="text-xs text-muted/80">
              Actions like creating, updating, and completing tasks will appear here.
            </p>
          </div>
        ) : (
          <div className="space-y-2.5 max-h-[calc(100vh-180px)] overflow-y-auto pr-1">
            {activities.map((activity: Activity) => (
              <div
                key={activity.id}
                className="flex items-start gap-3 p-3 rounded-xl bg-secondary/30 hover:bg-secondary/50 border border-border/60 transition-colors"
              >
                {getActivityIcon(activity.type, activity.message)}
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-foreground leading-snug break-words">
                    {activity.message}
                  </p>
                  {activity.createdAt && (
                    <span className="text-[11px] text-muted block mt-1">
                      {formatTimeAgo(activity.createdAt)}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Drawer>
  );
};

export default ActivityDrawer;
