import { useProjects } from "../projectQueries";
import { useProjectStore } from "../../../store/projectStore";
import { cn } from "../../../lib/cn";
import { motion } from "framer-motion";

export const ProjectSidebar = () => {
  const { data: projects = [] } = useProjects();

  const { selectedProjectId, setSelectedProjectId } = useProjectStore();

  return (
    <div className="mt-6">
      <h2 className="text-sm font-semibold text-gray-500 px-3 mb-2">
        Projects
      </h2>
      <motion.div layout className="space-y-1">
        <div className="space-y-1">
          {projects.map((project) => (
            <button
              key={project.id}
              onClick={() => setSelectedProjectId(project.id)}
              className={cn(
                `
                  w-full flex items-center
                  gap-3 rounded-xl
                  px-3 py-2.5
                  text-sm font-medium
                  transition-all
                `,

                selectedProjectId === project.id
                  ? `
                    bg-secondary
                    text-foreground
                  `
                  : `
                    hover:bg-secondary/70
                    text-muted
                  `,
              )}
            >
              <div
                className="w-3 h-3 rounded-full"
                style={{
                  backgroundColor: project.color,
                }}
              />

              <span>{project.name}</span>
            </button>
          ))}
        </div>
      </motion.div>
    </div>
  );
};
