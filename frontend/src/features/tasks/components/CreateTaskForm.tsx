import { useState } from "react";

type Props = {
  onCreate: (title: string, description: string) => void;
};

export const CreateTaskForm = ({ onCreate }: Props) => {
  const [title, setTitle] = useState("");

  const [description, setDescription] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!title.trim()) {
      return;
    }

    onCreate(title, description);

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

      <button type="submit" className="bg-black text-white px-4 py-2 rounded">
        Add Task
      </button>
    </form>
  );
};
