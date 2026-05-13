import { Trash2 } from "lucide-react";

import type { Task } from "../taskTypes";

import { PriorityBadge } from "../../../components/ui/PriorityBadge";

import { formatDate } from "../../../utils/date";

type Props = {
  task: Task;

  onToggle: () => void;
  onDelete: () => void;
  onEdit: () => void;
};

export const TaskCard = ({ task, onToggle, onDelete, onEdit }: Props) => {
  return (
    <div className="bg-white rounded-lg p-4 shadow-sm border">
      <div className="flex items-start justify-between gap-3">
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
            <div className="flex items-center gap-2 mt-3">
              <PriorityBadge priority={task.priority} />

              {task.dueDate && (
                <span className="text-xs text-gray-500">
                  Due {formatDate(task.dueDate)}
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center">
          <button
            onClick={onEdit}
            className="text-sm text-gray-500 hover:text-black mr-3"
          >
            Edit
          </button>

          <button
            onClick={onDelete}
            className="text-red-500 hover:text-red-700"
          >
            <Trash2 size={18} />
          </button>
        </div>
      </div>
    </div>
  );
};
