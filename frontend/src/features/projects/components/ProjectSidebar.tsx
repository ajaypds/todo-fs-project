import { useState } from "react";
import { useProjects, useDeleteProject } from "../projectQueries";
import type { Project } from "../projectTypes";
import { useViewStore } from "../../../store/viewStore";
import { cn } from "../../../lib/cn";
import { motion } from "framer-motion";
import { Plus, Trash2 } from "lucide-react";
import CreateProjectModal from "./CreateProjectModal";
import Modal from "../../../components/ui/Modal";
import { Button } from "../../../components/ui/Button";
import toast from "react-hot-toast";

type Props = {
  collapsed?: boolean;
};

export const ProjectSidebar = ({ collapsed = false }: Props) => {
  const { data: projects = [] } = useProjects();
  const { selectedProjectId, setSelectedProjectId } = useViewStore();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [projectToDelete, setProjectToDelete] = useState<Project | null>(null);
  const deleteProjectMutation = useDeleteProject();

  if (collapsed) {
    return (
      <div className="mt-4 flex flex-col items-center">
        <button
          onClick={() => setCreateModalOpen(true)}
          className="w-10 h-10 rounded-xl flex items-center justify-center text-muted hover:text-foreground hover:bg-secondary transition-colors cursor-pointer mb-2"
          title="Create Project"
          type="button"
        >
          <Plus size={16} />
        </button>

        <div className="space-y-1.5 flex flex-col items-center w-full">
          {projects.map((project) => (
            <button
              key={project.id}
              onClick={() =>
                setSelectedProjectId(
                  selectedProjectId === project.id ? null : project.id
                )
              }
              className={cn(
                "w-10 h-10 rounded-xl flex items-center justify-center transition-all cursor-pointer relative group",
                selectedProjectId === project.id
                  ? "bg-secondary shadow-xs"
                  : "hover:bg-secondary/70 text-muted"
              )}
              title={project.name}
              type="button"
            >
              <div
                className="w-3 h-3 rounded-full transition-transform group-hover:scale-125"
                style={{ backgroundColor: project.color }}
              />
            </button>
          ))}
        </div>

        <CreateProjectModal
          open={createModalOpen}
          onClose={() => setCreateModalOpen(false)}
        />
      </div>
    );
  }

  return (
    <div className="mt-6">
      <div className="flex items-center justify-between px-3 mb-2">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-muted">
          Projects
        </h2>
        <button
          onClick={() => setCreateModalOpen(true)}
          className="p-1 rounded-md text-muted hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
          title="Create Project"
          type="button"
        >
          <Plus size={15} />
        </button>
      </div>

      <motion.div layout className="space-y-1">
        <div className="space-y-1">
          {projects.length === 0 && (
            <p className="text-xs text-muted px-3 py-1 italic">
              No projects yet
            </p>
          )}

          {projects.map((project) => (
            <div
              key={project.id}
              className="group relative flex items-center"
            >
              <button
                onClick={() =>
                  setSelectedProjectId(
                    selectedProjectId === project.id ? null : project.id
                  )
                }
                className={cn(
                  `
                    w-full flex items-center
                    gap-3 rounded-xl
                    px-3 py-2 pr-8
                    text-sm font-medium
                    transition-all cursor-pointer
                  `,
                  selectedProjectId === project.id
                    ? `
                      bg-secondary
                      text-foreground
                    `
                    : `
                      hover:bg-secondary/70
                      text-muted
                    `
                )}
              >
                <div
                  className="w-2.5 h-2.5 rounded-full shrink-0"
                  style={{
                    backgroundColor: project.color,
                  }}
                />
                <span className="truncate">{project.name}</span>
              </button>

              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setProjectToDelete(project);
                }}
                className="absolute right-2 opacity-0 group-hover:opacity-100 p-1 text-muted hover:text-red-500 rounded-md transition-all cursor-pointer"
                title={`Delete ${project.name}`}
              >
                <Trash2 size={13} />
              </button>
            </div>
          ))}
        </div>
      </motion.div>

      <CreateProjectModal
        open={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
      />

      {/* Delete Project Confirmation Modal */}
      <Modal
        open={!!projectToDelete}
        onClose={() => setProjectToDelete(null)}
        title="Delete Project"
      >
        <div className="space-y-4 text-sm">
          <p className="text-muted leading-relaxed">
            Are you sure you want to delete <strong className="text-foreground">{projectToDelete?.name}</strong>?
          </p>
          <p className="text-xs text-muted/80 bg-secondary/40 p-3 rounded-lg border border-border/80">
            ℹ️ Tasks assigned to this project will <strong>not be deleted</strong>. They will be moved to your <strong>Inbox</strong>.
          </p>
          <div className="flex justify-end gap-2 pt-3 border-t border-border">
            <Button
              variant="ghost"
              onClick={() => setProjectToDelete(null)}
              disabled={deleteProjectMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={deleteProjectMutation.isPending}
              onClick={() => {
                if (!projectToDelete) return;
                deleteProjectMutation.mutate(projectToDelete.id, {
                  onSuccess: () => {
                    toast.success(`Project "${projectToDelete.name}" deleted`);
                    if (selectedProjectId === projectToDelete.id) {
                      setSelectedProjectId(null);
                    }
                    setProjectToDelete(null);
                  },
                  onError: () => {
                    toast.error("Failed to delete project");
                  },
                });
              }}
              className="gap-1.5"
            >
              <Trash2 size={14} />
              <span>{deleteProjectMutation.isPending ? "Deleting..." : "Delete Project"}</span>
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

