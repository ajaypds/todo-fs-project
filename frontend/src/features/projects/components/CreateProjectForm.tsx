import { useState } from "react";

type Props = {
  onCreate: (payload: { name: string; color: string }) => void;
};

const colors = [
  "#ef4444",
  "#f97316",
  "#eab308",
  "#22c55e",
  "#3b82f6",
  "#8b5cf6",
];

export const CreateProjectForm = ({ onCreate }: Props) => {
  const [name, setName] = useState("");

  const [color, setColor] = useState(colors[0]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!name.trim()) {
      return;
    }

    onCreate({
      name,
      color,
    });

    setName("");
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-white rounded-lg border p-4 mb-6"
    >
      <h2 className="font-semibold mb-4">Create Project</h2>

      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="Project name"
        className="w-full border rounded p-3 mb-4"
      />

      <div className="flex gap-2 mb-4">
        {colors.map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => setColor(item)}
            className={`
              w-8 h-8 rounded-full border-2

              ${color === item ? "border-black" : "border-transparent"}
            `}
            style={{
              backgroundColor: item,
            }}
          />
        ))}
      </div>

      <button type="submit" className="bg-black text-white px-4 py-2 rounded">
        Create Project
      </button>
    </form>
  );
};
