import { useState } from "react";
import { Modal } from "../../../components/ui/Modal";
import { Input } from "../../../components/ui/Input";
import { Button } from "../../../components/ui/Button";
import { useCreateWorkspace } from "../workspaceQueries";
import { useWorkspaceStore } from "../../../store/workspaceStore";
import toast from "react-hot-toast";

type CreateWorkspaceModalProps = {
  isOpen: boolean;
  onClose: () => void;
};

export const CreateWorkspaceModal = ({
  isOpen,
  onClose,
}: CreateWorkspaceModalProps) => {
  const [name, setName] = useState("");
  const createMutation = useCreateWorkspace();
  const setActiveWorkspaceId = useWorkspaceStore(
    (state) => state.setActiveWorkspaceId
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    createMutation.mutate(
      { name: name.trim() },
      {
        onSuccess: (newWorkspace) => {
          setActiveWorkspaceId(newWorkspace.id);
          toast.success(`Workspace "${newWorkspace.name}" created!`);
          setName("");
          onClose();
        },
        onError: (err: any) => {
          const msg =
            err.response?.data?.message || "Failed to create workspace";
          toast.error(msg);
        },
      }
    );
  };

  return (
    <Modal open={isOpen} onClose={onClose} title="Create New Workspace">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-foreground mb-1">
            Workspace Name
          </label>
          <Input
            placeholder="e.g. Acme Engineering, Product Team"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
          />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            disabled={!name.trim() || createMutation.isPending}
          >
            {createMutation.isPending ? "Creating..." : "Create Workspace"}
          </Button>
        </div>
      </form>
    </Modal>
  );
};
