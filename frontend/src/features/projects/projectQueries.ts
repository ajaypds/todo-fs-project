import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createProject, deleteProject, getProjects } from "./projectApi";
import type { Project } from "./projectTypes";
import toast from "react-hot-toast";

export const useProjects = () => {
  return useQuery({
    queryKey: ["projects"],
    queryFn: getProjects,
  });
};

type CreateProjectContext = {
  previousProjects: Project[] | undefined;
  optimisticId: string;
};

export const useCreateProject = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createProject,

    onMutate: async (newProject) => {
      await queryClient.cancelQueries({ queryKey: ["projects"] });
      const previousProjects = queryClient.getQueryData<Project[]>(["projects"]);
      const optimisticId = crypto.randomUUID();

      const optimisticProject: Project = {
        id: optimisticId,
        name: newProject.name,
        color: newProject.color || "#3b82f6",
      };

      queryClient.setQueryData<Project[]>(["projects"], (old = []) => [
        ...old,
        optimisticProject,
      ]);

      return { previousProjects, optimisticId };
    },

    onSuccess: (savedProject, _variables, context: CreateProjectContext | undefined) => {
      queryClient.setQueryData<Project[]>(["projects"], (old = []) =>
        old.map((p) => (p.id === context?.optimisticId ? savedProject : p))
      );
    },

    onError: (_error, _variables, context: CreateProjectContext | undefined) => {
      if (context?.previousProjects) {
        queryClient.setQueryData(["projects"], context.previousProjects);
      }
      toast.error("Failed to create project. Reverted.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({
        queryKey: ["projects"],
      });
    },
  });
};

export const useDeleteProject = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteProject,

    onMutate: async (projectId: string) => {
      await queryClient.cancelQueries({ queryKey: ["projects"] });
      const previousProjects = queryClient.getQueryData<Project[]>(["projects"]);

      queryClient.setQueryData<Project[]>(["projects"], (old = []) =>
        old.filter((p) => p.id !== projectId)
      );

      return { previousProjects };
    },

    onError: (_error, _projectId, context) => {
      if (context?.previousProjects) {
        queryClient.setQueryData(["projects"], context.previousProjects);
      }
      toast.error("Failed to delete project. Reverted.");
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
};