import { apiClient } from "../../api/client";

export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";

export type WorkspaceResponse = {
  id: string;
  name: string;
  ownerId: string;
  ownerName: string;
  currentUserRole: WorkspaceRole;
  memberCount: number;
  createdAt: string;
};

export type WorkspaceMemberResponse = {
  id: string;
  userId: string;
  email: string;
  fullName?: string;
  role: WorkspaceRole;
  joinedAt: string;
};

export type CreateWorkspaceRequest = {
  name: string;
};

export type UpdateWorkspaceRequest = {
  name: string;
};

export type AddWorkspaceMemberRequest = {
  email: string;
  role: WorkspaceRole;
};

export type UpdateMemberRoleRequest = {
  role: WorkspaceRole;
};

export const listWorkspaces = async (): Promise<WorkspaceResponse[]> => {
  const response = await apiClient.get<WorkspaceResponse[]>("/workspaces");
  return response.data;
};

export const getWorkspace = async (workspaceId: string): Promise<WorkspaceResponse> => {
  const response = await apiClient.get<WorkspaceResponse>(`/workspaces/${workspaceId}`);
  return response.data;
};

export const createWorkspace = async (request: CreateWorkspaceRequest): Promise<WorkspaceResponse> => {
  const response = await apiClient.post<WorkspaceResponse>("/workspaces", request);
  return response.data;
};

export const updateWorkspace = async (workspaceId: string, request: UpdateWorkspaceRequest): Promise<WorkspaceResponse> => {
  const response = await apiClient.patch<WorkspaceResponse>(`/workspaces/${workspaceId}`, request);
  return response.data;
};

export const deleteWorkspace = async (workspaceId: string): Promise<void> => {
  await apiClient.delete(`/workspaces/${workspaceId}`);
};

export const listWorkspaceMembers = async (workspaceId: string): Promise<WorkspaceMemberResponse[]> => {
  const response = await apiClient.get<WorkspaceMemberResponse[]>(`/workspaces/${workspaceId}/members`);
  return response.data;
};

export const addWorkspaceMember = async (
  workspaceId: string,
  request: AddWorkspaceMemberRequest
): Promise<WorkspaceMemberResponse> => {
  const response = await apiClient.post<WorkspaceMemberResponse>(
    `/workspaces/${workspaceId}/members`,
    request
  );
  return response.data;
};

export const updateWorkspaceMemberRole = async (
  workspaceId: string,
  targetUserId: string,
  request: UpdateMemberRoleRequest
): Promise<WorkspaceMemberResponse> => {
  const response = await apiClient.patch<WorkspaceMemberResponse>(
    `/workspaces/${workspaceId}/members/${targetUserId}`,
    request
  );
  return response.data;
};

export const removeWorkspaceMember = async (
  workspaceId: string,
  targetUserId: string
): Promise<void> => {
  await apiClient.delete(`/workspaces/${workspaceId}/members/${targetUserId}`);
};
