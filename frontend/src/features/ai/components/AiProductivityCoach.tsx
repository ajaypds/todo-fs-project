import { useMemo } from "react";
import { useProductivityCoach } from "../api/productivityCoachQueries";
import type { Task } from "../../tasks/types/taskTypes";
import { Button } from "../../../components/ui/Button";

type Props = {
  tasks: Task[];
};

export const AiProductivityCoach = ({ tasks }: Props) => {
  const mutation = useProductivityCoach();

  const taskSnapshots = useMemo(() => {
    const priorityMap: Record<number, string> = {
      1: "Low",
      2: "Medium",
      3: "High",
      4: "Urgent",
    };

    return tasks.map((task) => ({
      title: task.title,
      priority: priorityMap[task.priority] ?? "Low",
      completed: task.completed,
      dueDate: task.dueDate,
    }));
  }, [tasks]);

  const generateInsights = () => {
    mutation.mutate(taskSnapshots);
  };

  return (
    <div className="space-y-4">
      <Button
        onClick={generateInsights}
        disabled={mutation.isPending}
        className="
          px-4 py-2
          rounded-xl
          bg-accent
          text-white
          disabled:opacity-50
          disabled:cursor-not-allowed
        "
      >
        🧠 Generate Insights
      </Button>

      {mutation.isPending && <div>Analyzing workload...</div>}

      {mutation.data && (
        <div
          className="
              rounded-2xl
              border border-border
              bg-card
              p-4
              space-y-4
            "
        >
          <h3 className="font-semibold">Productivity Summary</h3>

          <p>{mutation.data.summary}</p>

          <ul
            className="
                list-disc
                pl-5
                space-y-2
              "
          >
            {mutation.data.recommendations.map((recommendation, index) => (
              <li key={index}>{recommendation}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};
