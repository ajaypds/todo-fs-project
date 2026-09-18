import { useState, useEffect } from "react";
import Drawer from "../../../components/ui/Drawer";
import type { Task } from "../types/taskTypes";
import type { Project } from "../../projects/projectTypes";
import type { Label } from "../../labels/labelTypes";
import { useUpdateTask, useDeleteTask } from "../api/taskQueries";
import { PriorityBadge } from "../../../components/ui/PriorityBadge";
import { formatDate } from "../../../utils/date";
import { Input } from "../../../components/ui/Input";
import { Textarea } from "../../../components/ui/Textarea";
import { Select } from "../../../components/ui/Select";
import { Button } from "../../../components/ui/Button";
import { Trash2, Calendar, Folder, Tag, Clock, CheckCircle2, Circle } from "lucide-react";
import toast from "react-hot-toast";
import { cn } from "../../../lib/cn";

type Props = {
  task: Task | null;
  open: boolean;
  onClose: () => void;
  projects: Project[];
  labels: Label[];
};

export const TaskDetailsDrawer = ({
  task,
  open,
  onClose,
  projects,
  labels,
}: Props) => {
  const updateTaskMutation = useUpdateTask();
  const deleteTaskMutation = useDeleteTask();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<number>(1);
  const [projectId, setProjectId] = useState<string>("");
  const [dueDate, setDueDate] = useState<string>("");
  const [selectedLabels, setSelectedLabels] = useState<string[]>([]);
  const [completed, setCompleted] = useState(false);

  useEffect(() => {
    if (!task) return;
    setTitle(task.title || "");
    setDescription(task.description || "");
    setPriority(task.priority || 1);
    setProjectId(task.projectId || "");
    setDueDate(task.dueDate ? task.dueDate.split("T")[0] : "");
    setSelectedLabels(task.labels ? task.labels.map((l) => l.id) : []);
    setCompleted(task.completed || false);
  }, [task]);

  if (!task) return null;

  const handleSave = () => {
    if (!title.trim()) {
      toast.error("Task title cannot be empty");
      return;
    }

    updateTaskMutation.mutate(
      {
        taskId: task.id,
        payload: {
          title: title.trim(),
          description: description.trim(),
          priority,
          projectId: projectId || null,
          dueDate: dueDate ? `${dueDate}T00:00:00` : null,
          labelIds: selectedLabels,
          completed,
        },
      },
      {
        onSuccess: () => {
          toast.success("Task updated");
          onClose();
        },
        onError: () => {
          toast.error("Failed to update task");
        },
      }
    );
  };

  const handleDelete = () => {
    if (window.confirm("Are you sure you want to delete this task?")) {
      deleteTaskMutation.mutate(task.id, {
        onSuccess: () => {
          toast.success("Task deleted");
          onClose();
        },
        onError: () => {
          toast.error("Failed to delete task");
        },
      });
    }
  };

  const handleToggleComplete = () => {
    const nextState = !completed;
    setCompleted(nextState);
    updateTaskMutation.mutate(
      {
        taskId: task.id,
        payload: { completed: nextState },
      },
      {
        onSuccess: () => {
          toast.success(nextState ? "Task completed" : "Task marked active");
        },
      }
    );
  };

  const currentProject = projects.find((p) => p.id === projectId);

  return (
    <Drawer open={open} onClose={onClose} title="Task Details">
      <div className="flex flex-col h-full space-y-6 pb-8">
        {/* Completion status toggle button */}
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <button
            type="button"
            onClick={handleToggleComplete}
            className="flex items-center gap-2 text-sm font-medium transition-colors hover:text-accent cursor-pointer"
          >
            {completed ? (
              <>
                <CheckCircle2 size={18} className="text-emerald-500" />
                <span className="text-emerald-600 dark:text-emerald-400 font-semibold">
                  Completed
                </span>
              </>
            ) : (
              <>
                <Circle size={18} className="text-muted" />
                <span className="text-muted">Mark as Complete</span>
              </>
            )}
          </button>

          <PriorityBadge priority={priority} />
        </div>

        {/* Title Input */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
            Title
          </label>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Task title"
            className="font-medium text-base"
          />
        </div>

        {/* Description Textarea */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
            Description & Notes
          </label>
          <Textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Add detailed notes or requirements..."
            className="min-h-28 text-sm"
          />
        </div>

        {/* Attributes Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 rounded-xl bg-secondary/50 border border-border">
          {/* Project */}
          <div>
            <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
              <Folder size={13} />
              <span>Project</span>
            </div>
            <Select
              value={projectId}
              onChange={(e) => setProjectId(e.target.value)}
              className="text-xs"
            >
              <option value="">No Project (Inbox)</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
            {currentProject && (
              <div className="flex items-center gap-1.5 mt-1.5 px-1">
                <div
                  className="w-2 h-2 rounded-full"
                  style={{ backgroundColor: currentProject.color }}
                />
                <span className="text-xs text-muted">{currentProject.name}</span>
              </div>
            )}
          </div>

          {/* Priority */}
          <div>
            <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
              <span>Priority</span>
            </div>
            <Select
              value={priority}
              onChange={(e) => setPriority(Number(e.target.value))}
              className="text-xs"
            >
              <option value={1}>Low</option>
              <option value={2}>Medium</option>
              <option value={3}>High</option>
              <option value={4}>Urgent</option>
            </Select>
          </div>

          {/* Due Date */}
          <div className="sm:col-span-2">
            <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
              <Calendar size={13} />
              <span>Due Date</span>
            </div>
            <div className="flex items-center gap-2">
              <Input
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                className="text-xs flex-1"
              />
              {dueDate && (
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setDueDate("")}
                  className="text-xs py-1.5 px-2"
                >
                  Clear
                </Button>
              )}
            </div>
            {dueDate && (
              <p className="text-xs text-muted mt-1 px-1">
                Scheduled for {formatDate(`${dueDate}T00:00:00`)}
              </p>
            )}
          </div>
        </div>

        {/* Labels Selection */}
        <div>
          <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted mb-2">
            <Tag size={13} />
            <span>Labels</span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {labels.length === 0 && (
              <span className="text-xs text-muted italic">No labels available</span>
            )}
            {labels.map((label) => {
              const isSelected = selectedLabels.includes(label.id);
              return (
                <button
                  key={label.id}
                  type="button"
                  onClick={() =>
                    setSelectedLabels((prev) =>
                      isSelected
                        ? prev.filter((id) => id !== label.id)
                        : [...prev, label.id]
                    )
                  }
                  className={cn(
                    "px-2.5 py-1 rounded-full text-xs font-medium border transition-all cursor-pointer",
                    isSelected
                      ? "text-white border-transparent"
                      : "bg-card border-border text-muted hover:text-foreground"
                  )}
                  style={{
                    backgroundColor: isSelected ? label.color : undefined,
                  }}
                >
                  {label.name}
                </button>
              );
            })}
          </div>
        </div>

        {/* Timestamps */}
        <div className="pt-2 border-t border-border flex items-center justify-between text-xs text-muted">
          <div className="flex items-center gap-1">
            <Clock size={12} />
            <span>Created {task.createdAt ? formatDate(task.createdAt) : "Recently"}</span>
          </div>
        </div>

        {/* Actions Bottom Bar */}
        <div className="flex items-center justify-between pt-4 border-t border-border mt-auto">
          <Button
            type="button"
            variant="ghost"
            onClick={handleDelete}
            className="text-red-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 gap-1.5"
          >
            <Trash2 size={16} />
            <span>Delete</span>
          </Button>

          <div className="flex items-center gap-2">
            <Button type="button" variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button
              type="button"
              variant="primary"
              onClick={handleSave}
              disabled={updateTaskMutation.isPending}
            >
              {updateTaskMutation.isPending ? "Saving..." : "Save Changes"}
            </Button>
          </div>
        </div>
      </div>
    </Drawer>
  );
};

export default TaskDetailsDrawer;
