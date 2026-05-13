import { useState } from "react";
import type { Project } from "../../projects/projectTypes";

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
    <form
      onSubmit={handleSubmit}
      className="bg-white p-4 rounded-lg shadow-sm border"
    >
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Task title"
        className="w-full border rounded p-3 mb-3"
      />

      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Description"
        className="w-full border rounded p-3 mb-3"
      />

      <div className="mb-3">
        <select
          value={priority}
          onChange={(e) => setPriority(Number(e.target.value))}
          className="w-full border rounded p-3"
        >
          <option value={1}>Priority 1</option>

          <option value={2}>Priority 2</option>

          <option value={3}>Priority 3</option>

          <option value={4}>Priority 4</option>
        </select>
      </div>

      <select
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
      </select>

      <div className="mb-3">
        <input
          type="date"
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          className="w-full border rounded p-3"
        />
      </div>

      <button type="submit" className="bg-black text-white px-4 py-2 rounded">
        Add Task
      </button>
    </form>
  );
};
