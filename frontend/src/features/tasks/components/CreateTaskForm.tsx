import { useEffect, useState } from "react";
import type { Project } from "../../projects/projectTypes";
import { Input } from "../../../components/ui/Input";
import { Textarea } from "../../../components/ui/Textarea";
import { Button } from "../../../components/ui/Button";
import { Select } from "../../../components/ui/Select";
import type { Label } from "../../labels/labelTypes";
import { cn } from "../../../lib/cn";
import type { ParsedTaskResponse } from "../../ai/types/aiTypes";
import { Card } from "../../../components/ui/Card";

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
    <Card className="p-4">
      <form onSubmit={handleSubmit} className="rounded-lg">
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Task title"
          className="w-full p-3 mb-3"
        />

        <Textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Description"
          className="w-full p-3 mb-3"
        />

        <div className="mb-3">
          <Select
            value={priority}
            onChange={(e) => setPriority(Number(e.target.value))}
            className="w-full border rounded p-3"
          >
            <option value={1}>Low</option>
            <option value={2}>Medium</option>
            <option value={3}>High</option>
            <option value={4}>Urgent</option>
          </Select>
        </div>

        <Select
          value={projectId}
          onChange={(e) => setProjectId(e.target.value)}
          className="w-full border rounded p-3 mb-3"
        >
          <option value="">No Project</option>

          {projects.map((project) => (
            <option key={project.id} value={project.id}>
              {project.name}
            </option>
          ))}
        </Select>

        <div>
          <label className="block text-sm font-medium mb-2">Labels</label>

          <div className="flex flex-wrap gap-2">
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
                    `
                    px-3 py-1.5
                    rounded-full
                    text-xs font-medium
                    border
                    transition-all
                    mb-3
                  `,

                    selected
                      ? `
                    text-white
                    border-transparent
                  `
                      : `
                    bg-card
                    border-border
                  `,
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

        <div className="mb-3">
          <Input
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            className="w-full p-3"
          />
        </div>

        <Button type="submit" variant="primary" className="px-4 py-2">
          Add Task
        </Button>
      </form>
    </Card>
  );
};
