import { Trash2 } from "lucide-react";

import type { Task } from "../types/taskTypes";

import { PriorityBadge } from "../../../components/ui/PriorityBadge";

import { formatDate } from "../../../utils/date";
import { Card } from "../../../components/ui/Card";
import { motion } from "framer-motion";
import { forwardRef } from "react";
import { LabelBadge } from "../../labels/components/LabelBadge";
import { cn } from "../../../lib/cn";

type Props = {
  task: Task;

  onToggle: () => void;
  onDelete: () => void;
  onEdit: () => void;
};

export const TaskCard = forwardRef<
  HTMLDivElement,
  Props & React.HTMLAttributes<HTMLDivElement>
>(({ task, onToggle, onDelete, onEdit, className, style, ...props }, ref) => {
  return (
    <Card
      ref={ref}
      style={style}
      className={cn(
        `p-5 hover:shadow-card ${className ?? ""}`,
        "transition-opacity",
        task.optimistic && "opacity-60",
      )}
      {...props}
    >
      <motion.div
        //   className="flex items-start justify-between gap-3"
        layout={false}
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -8 }}
      >
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

                {task.labels?.map((label) => (
                  <LabelBadge key={label.id} label={label} />
                ))}

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
      </motion.div>
    </Card>
  );
});

TaskCard.displayName = "TaskCard";
