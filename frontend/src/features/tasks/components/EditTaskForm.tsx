import { useState } from "react";

import type { Task } from "../types/taskTypes";

type Props = {
  task: Task;

  onSave: (payload: { title: string; description?: string }) => void;
};

export const EditTaskForm = ({ task, onSave }: Props) => {
  const [title, setTitle] = useState(task.title);

  const [description, setDescription] = useState(task.description ?? "");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    onSave({
      title,
      description,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        className="
          w-full border rounded-lg p-3
        "
      />

      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        className="
          w-full border rounded-lg p-3
        "
      />

      <button
        type="submit"
        className="
          bg-black text-white
          px-4 py-2 rounded-lg
        "
      >
        Save Changes
      </button>
    </form>
  );
};
