import { useState } from "react";
import type { Project } from "../../projects/projectTypes";
import { Input } from "../../../components/ui/Input";
import { Textarea } from "../../../components/ui/Textarea";
import { Button } from "../../../components/ui/Button";
import { Select } from "../../../components/ui/Select";
// import { Card } from "../../../components/ui/Card";

type Props = {
  projects: Project[];
  onCreate: (payload: {
    title: string;
    description: string;
    priority: number;
    dueDate?: string;
    projectId?: string;
  }) => void;
};

export const CreateTaskForm = ({ onCreate, projects }: Props) => {
  const [title, setTitle] = useState("");

  const [description, setDescription] = useState("");

  const [priority, setPriority] = useState(4);

  const [dueDate, setDueDate] = useState("");
  const [projectId, setProjectId] = useState("");

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
    });

    setTitle("");
    setDescription("");
  };

  return (
    // <Card>
    <form onSubmit={handleSubmit} className="bg-background p-4 rounded-lg">
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
          <option value={1}>Priority 1</option>

          <option value={2}>Priority 2</option>

          <option value={3}>Priority 3</option>

          <option value={4}>Priority 4</option>
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
    // </Card>
  );
};
