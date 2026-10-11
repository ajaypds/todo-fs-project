import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  listWorkspaces,
  getWorkspace,
  createWorkspace,
  updateWorkspace,
  deleteWorkspace,
  listWorkspaceMembers,
  addWorkspaceMember,
  updateWorkspaceMemberRole,
  removeWorkspaceMember,
} from "./workspaceApi";
import type {
  CreateWorkspaceRequest,
  UpdateWorkspaceRequest,
  AddWorkspaceMemberRequest,
  UpdateMemberRoleRequest,
} from "./workspaceApi";

export const useWorkspaces = () => {
  return useQuery({
    queryKey: ["workspaces"],
    queryFn: listWorkspaces,
  });
};

export const useWorkspace = (workspaceId?: string) => {
  return useQuery({
    queryKey: ["workspaces", workspaceId],
    queryFn: () => getWorkspace(workspaceId!),
    enabled: !!workspaceId,
  });
};

export const useWorkspaceMembers = (workspaceId?: string) => {
  return useQuery({
    queryKey: ["workspaces", workspaceId, "members"],
    queryFn: () => listWorkspaceMembers(workspaceId!),
    enabled: !!workspaceId,
  });
};

export const useCreateWorkspace = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateWorkspaceRequest) => createWorkspace(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    },
  });
};

export const useUpdateWorkspace = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      workspaceId,
      request,
    }: {
      workspaceId: string;
      request: UpdateWorkspaceRequest;
    }) => updateWorkspace(workspaceId, request),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
      queryClient.invalidateQueries({
        queryKey: ["workspaces", variables.workspaceId],
      });
    },
  });
};

export const useDeleteWorkspace = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (workspaceId: string) => deleteWorkspace(workspaceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    },
  });
};

export const useAddWorkspaceMember = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      workspaceId,
      request,
    }: {
      workspaceId: string;
      request: AddWorkspaceMemberRequest;
    }) => addWorkspaceMember(workspaceId, request),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["workspaces", variables.workspaceId, "members"],
      });
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    },
  });
};

export const useUpdateWorkspaceMemberRole = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      workspaceId,
      targetUserId,
      request,
    }: {
      workspaceId: string;
      targetUserId: string;
      request: UpdateMemberRoleRequest;
    }) => updateWorkspaceMemberRole(workspaceId, targetUserId, request),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["workspaces", variables.workspaceId, "members"],
      });
    },
  });
};

export const useRemoveWorkspaceMember = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      workspaceId,
      targetUserId,
    }: {
      workspaceId: string;
      targetUserId: string;
    }) => removeWorkspaceMember(workspaceId, targetUserId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["workspaces", variables.workspaceId, "members"],
      });
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    },
  });
};
