import { Modal } from "../../../components/ui/Modal";
import { useCreateProject } from "../projectQueries";
import toast from "react-hot-toast";
import { useState } from "react";
import { Input } from "../../../components/ui/Input";
import { Button } from "../../../components/ui/Button";

type Props = {
  open: boolean;
  onClose: () => void;
};

const colors = [
  "#3b82f6", // blue
  "#22c55e", // green
  "#eab308", // yellow
  "#f97316", // orange
  "#ef4444", // red
  "#8b5cf6", // purple
  "#ec4899", // pink
  "#6b7280", // gray
];

export const CreateProjectModal = ({ open, onClose }: Props) => {
  const [name, setName] = useState("");
  const [color, setColor] = useState(colors[0]);
  const createProjectMutation = useCreateProject();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!name.trim()) {
      return;
    }

    createProjectMutation.mutate(
      {
        name: name.trim(),
        color,
      },
      {
        onSuccess: () => {
          toast.success("Project created");
          setName("");
          setColor(colors[0]);
          onClose();
        },
        onError: () => {
          toast.error("Failed to create project");
        },
      }
    );
  };

  return (
    <Modal open={open} onClose={onClose} title="Create Project">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">
            Project Name
          </label>
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Work, Personal, Marketing"
            autoFocus
          />
        </div>

        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-muted mb-2">
            Color Accent
          </label>
          <div className="flex gap-2.5 flex-wrap">
            {colors.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setColor(item)}
                className={`w-7 h-7 rounded-full transition-transform hover:scale-110 cursor-pointer ${
                  color === item ? "ring-2 ring-offset-2 ring-primary scale-110" : ""
                }`}
                style={{
                  backgroundColor: item,
                }}
                aria-label={`Select color ${item}`}
              />
            ))}
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-3 border-t border-border">
          <Button
            type="button"
            variant="ghost"
            onClick={onClose}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            disabled={!name.trim() || createProjectMutation.isPending}
          >
            {createProjectMutation.isPending ? "Creating..." : "Create Project"}
          </Button>
        </div>
      </form>
    </Modal>
  );
};

export default CreateProjectModal;
