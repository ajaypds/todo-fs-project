import { useEffect, useState } from "react";
import type { Project } from "../../projects/projectTypes";
import { Input } from "../../../components/ui/Input";
import { Textarea } from "../../../components/ui/Textarea";
import { Button } from "../../../components/ui/Button";
import { Select } from "../../../components/ui/Select";
import { cn } from "../../../lib/cn";
import type { Label } from "../../labels/labelTypes";
import type { ParsedTaskResponse } from "../../ai/types/aiTypes";

type Props = {
  projects: Project[];
  labels: Label[];
  parsedTask?: ParsedTaskResponse | null;

  onCreate: (payload: {
    title: string;
    description: string;
    priority: number;
    dueDate?: string;
    projectId?: string;
    labelIds?: string[];
  }) => void;
};

export const CreateTaskForm = ({
  onCreate,
  projects,
  labels,
  parsedTask,
}: Props) => {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState(1);
  const [dueDate, setDueDate] = useState("");
  const [projectId, setProjectId] = useState("");
  const [selectedLabels, setSelectedLabels] = useState<string[]>([]);

  useEffect(() => {
    if (!parsedTask) return;

    // eslint-disable-next-line react-hooks/set-state-in-effect
    setTitle(parsedTask.title ?? "");
    setDescription(parsedTask.description ?? "");
    setPriority(parsedTask.priority ?? 1);
    setDueDate(parsedTask.dueDate?.split("T")[0] ?? "");
    console.log("Parsed Task: ", parsedTask);
  }, [parsedTask]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!title.trim()) {
      return;
    }

    onCreate({
      title,
      description,
      priority,
      dueDate: dueDate ? `${dueDate}T00:00:00` : undefined,
      projectId: projectId || undefined,
      labelIds: selectedLabels,
    });

    setTitle("");
    setDescription("");
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
          Title
        </label>
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="What do you need to do?"
          className="w-full text-sm font-medium"
          autoFocus
        />
      </div>

      <div>
        <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
          Description
        </label>
        <Textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Add details, links, or notes..."
          className="w-full text-sm min-h-20"
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
            Priority
          </label>
          <Select
            value={priority}
            onChange={(e) => setPriority(Number(e.target.value))}
            className="w-full text-xs"
          >
            <option value={1}>Low</option>
            <option value={2}>Medium</option>
            <option value={3}>High</option>
            <option value={4}>Urgent</option>
          </Select>
        </div>

        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
            Project
          </label>
          <Select
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
            className="w-full text-xs"
          >
            <option value="">No Project (Inbox)</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <div>
        <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
          Due Date
        </label>
        <Input
          type="date"
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          className="w-full text-xs"
        />
      </div>

      {labels.length > 0 && (
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
            Labels
          </label>
          <div className="flex flex-wrap gap-1.5">
            {labels.map((label) => {
              const selected = selectedLabels.includes(label.id);
              return (
                <button
                  key={label.id}
                  type="button"
                  onClick={() => {
                    setSelectedLabels((prev) =>
                      selected
                        ? prev.filter((id) => id !== label.id)
                        : [...prev, label.id],
                    );
                  }}
                  className={cn(
                    "px-2.5 py-1 rounded-full text-xs font-medium border transition-all cursor-pointer",
                    selected
                      ? "text-white border-transparent"
                      : "bg-card border-border text-muted hover:text-foreground",
                  )}
                  style={{
                    backgroundColor: selected ? label.color : undefined,
                  }}
                >
                  {label.name}
                </button>
              );
            })}
          </div>
        </div>
      )}

      <div className="flex justify-end gap-2 pt-3 border-t border-border">
        <Button type="submit" variant="primary" className="px-5 py-2">
          Create Task
        </Button>
      </div>
    </form>
  );
};

export default CreateTaskForm;
