import { Trash2, Calendar } from "lucide-react";
import type { Task } from "../types/taskTypes";
import type { Project } from "../../projects/projectTypes";
import { PriorityBadge } from "../../../components/ui/PriorityBadge";
import { formatDate } from "../../../utils/date";
import { Card } from "../../../components/ui/Card";
import { motion } from "framer-motion";
import { forwardRef } from "react";
import { LabelBadge } from "../../labels/components/LabelBadge";
import { cn } from "../../../lib/cn";

type Props = {
  task: Task;
  project?: Project;
  onToggle: () => void;
  onDelete: () => void;
  onEdit?: () => void;
  onSelect?: () => void;
};

export const TaskCard = forwardRef<
  HTMLDivElement,
  Props & React.HTMLAttributes<HTMLDivElement>
>(
  (
    { task, project, onToggle, onDelete, onEdit, onSelect, className, style, ...props },
    ref
  ) => {
    const isOverdue =
      task.dueDate &&
      !task.completed &&
      new Date(task.dueDate) < new Date(new Date().setHours(0, 0, 0, 0));

    return (
      <Card
        ref={ref}
        style={style}
        onClick={onSelect}
        className={cn(
          "group px-4 py-3 hover:shadow-md transition-all duration-150 border-border/80 hover:border-border cursor-pointer select-none",
          task.completed && "bg-secondary/30 opacity-75",
          task.optimistic && "opacity-60",
          className ?? ""
        )}
        {...props}
      >
        <motion.div
          layout={false}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          className="flex items-center justify-between gap-3"
        >
          {/* Left Side: Checkbox + Title + Metadata */}
          <div className="flex items-center gap-3 min-w-0 flex-1">
            {/* Checkbox */}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onToggle();
              }}
              className="shrink-0 p-1 -m-1 rounded-full text-muted hover:text-foreground cursor-pointer focus:outline-none"
              aria-label={task.completed ? "Mark incomplete" : "Mark complete"}
            >
              <input
                type="checkbox"
                checked={task.completed}
                readOnly
                className="w-4 h-4 rounded border-border text-accent focus:ring-accent/20 cursor-pointer pointer-events-none"
              />
            </button>

            {/* Title & Metadata chips */}
            <div className="flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-3 min-w-0 flex-1">
              <span
                className={cn(
                  "text-sm font-medium truncate",
                  task.completed
                    ? "line-through text-muted"
                    : "text-foreground group-hover:text-accent transition-colors"
                )}
              >
                {task.title}
              </span>

              {/* Badges / Metadata */}
              <div className="flex items-center gap-2 flex-wrap shrink-0">
                {/* Project Badge */}
                {project && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-medium bg-secondary text-muted">
                    <span
                      className="w-1.5 h-1.5 rounded-full"
                      style={{ backgroundColor: project.color }}
                    />
                    <span className="truncate max-w-24">{project.name}</span>
                  </span>
                )}

                {/* Priority */}
                <PriorityBadge priority={task.priority} />

                {/* Due Date */}
                {task.dueDate && (
                  <span
                    className={cn(
                      "inline-flex items-center gap-1 text-xs",
                      isOverdue
                        ? "text-red-500 font-semibold"
                        : "text-muted"
                    )}
                  >
                    <Calendar size={12} />
                    <span>{formatDate(task.dueDate)}</span>
                  </span>
                )}

                {/* Labels */}
                {task.labels && task.labels.length > 0 && (
                  <div className="hidden md:flex items-center gap-1">
                    {task.labels.map((label) => (
                      <LabelBadge key={label.id} label={label} />
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Right Side: Quick Action Buttons (visible on hover or focus) */}
          <div className="flex items-center gap-1 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
            {onEdit && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onEdit();
                }}
                className="text-xs px-2 py-1 rounded-md text-muted hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                Edit
              </button>
            )}

            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onDelete();
              }}
              className="p-1 rounded-md text-muted hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30 transition-colors cursor-pointer"
              title="Delete task"
            >
              <Trash2 size={15} />
            </button>
          </div>
        </motion.div>
      </Card>
    );
  }
);

TaskCard.displayName = "TaskCard";
export default TaskCard;
