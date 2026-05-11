import { Trash2 } from "lucide-react";

import type { Task } from "../taskTypes";

type Props = {
  task: Task;

  onToggle: () => void;

  onDelete: () => void;
};

export const TaskCard = ({ task, onToggle, onDelete }: Props) => {
  return (
    <div className="bg-white rounded-lg p-4 shadow-sm border">
      <div className="flex items-start justify-between">
        <div className="flex items-start gap-3">
          <input
            type="checkbox"
            checked={task.completed}
            onChange={onToggle}
            className="mt-1"
          />

          <div>
            <h3
              className={`font-medium ${
                task.completed ? "line-through text-gray-400" : ""
              }`}
            >
              {task.title}
            </h3>

            {task.description && (
              <p className="text-sm text-gray-500 mt-1">{task.description}</p>
            )}
          </div>
        </div>

        <button onClick={onDelete} className="text-red-500 hover:text-red-700">
          <Trash2 size={18} />
        </button>
      </div>
    </div>
  );
};
