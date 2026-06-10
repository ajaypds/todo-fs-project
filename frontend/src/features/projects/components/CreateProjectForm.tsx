import { useState } from "react";
import { Input } from "../../../components/ui/Input";
import { Button } from "../../../components/ui/Button";
import { Card } from "../../../components/ui/Card";

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
    <Card className="p-4 mb-4">
      <form onSubmit={handleSubmit} className="rounded-lg">
        <h2 className="font-semibold mb-4">Create Project</h2>

        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Project name"
          className="w-full border p-3 mb-4"
        />

        <div className="flex gap-2 mb-4">
          {colors.map((item) => (
            <Button
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

        <Button type="submit" variant="primary" className="px-4 py-2">
          Create Project
        </Button>
      </form>
    </Card>
  );
};
