import { useState } from "react";
import { useProjects } from "../projectQueries";
import { useViewStore } from "../../../store/viewStore";
import { cn } from "../../../lib/cn";
import { motion } from "framer-motion";
import { Plus } from "lucide-react";
import CreateProjectModal from "./CreateProjectModal";

type Props = {
  collapsed?: boolean;
};

export const ProjectSidebar = ({ collapsed = false }: Props) => {
  const { data: projects = [] } = useProjects();
  const { selectedProjectId, setSelectedProjectId } = useViewStore();
  const [createModalOpen, setCreateModalOpen] = useState(false);

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

