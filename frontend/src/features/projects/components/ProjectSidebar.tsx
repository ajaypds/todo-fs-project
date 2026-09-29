import { useState } from "react";
import { useProjects } from "../projectQueries";
import { useViewStore } from "../../../store/viewStore";
import { cn } from "../../../lib/cn";
import { motion } from "framer-motion";
import { Plus } from "lucide-react";
import CreateProjectModal from "./CreateProjectModal";

export const ProjectSidebar = () => {
  const { data: projects = [] } = useProjects();
  const { selectedProjectId, setSelectedProjectId } = useViewStore();
  const [createModalOpen, setCreateModalOpen] = useState(false);

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
            <button
              key={project.id}
              onClick={() =>
                setSelectedProjectId(
                  selectedProjectId === project.id ? null : project.id
                )
              }
              className={cn(
                `
                  w-full flex items-center
                  gap-3 rounded-xl
                  px-3 py-2
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
          ))}
        </div>
      </motion.div>

      <CreateProjectModal
        open={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
      />
    </div>
  );
};

