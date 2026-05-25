import { useActivities } from "../api/activityQueries";
import type { Activity } from "../api/activityTypes";

export const ActivityFeed = () => {
  const { data = [] } = useActivities();

  return (
    <>
      <div className="text-sm font-semibold mb-4 mt-4">Activity Feed:</div>
      <div className="space-y-3">
        {data.map((activity: Activity) => (
          <div
            key={activity.id}
            className="
              text-sm
              text-muted
              border-b border-border
              pb-2
            "
          >
            {activity.message}
          </div>
        ))}
      </div>
    </>
  );
};
