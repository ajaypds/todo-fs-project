import { useProjects } from "../projectQueries";

import { useProjectStore } from "../../../store/projectStore";

export const ProjectSidebar = () => {
  const { data: projects = [] } = useProjects();

  const { selectedProjectId, setSelectedProjectId } = useProjectStore();

  return (
    <div className="mt-6">
      <h2 className="text-sm font-semibold text-gray-500 px-3 mb-2">
        Projects
      </h2>

      <div className="space-y-1">
        {projects.map((project) => (
          <button
            key={project.id}
            onClick={() => setSelectedProjectId(project.id)}
            className={`
              w-full flex items-center gap-3
              text-left px-3 py-2 rounded-lg
              transition

              ${
                selectedProjectId === project.id
                  ? "bg-gray-100"
                  : "hover:bg-gray-50"
              }
            `}
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
    </div>
  );
};
