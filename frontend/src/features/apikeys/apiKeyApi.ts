import { apiClient } from "../../api/client";

export type ApiKeyItem = {
  id: string;
  name: string;
  keyPrefix: string;
  apiKey?: string; // only returned when generated
  createdAt: string;
  lastUsedAt?: string | null;
};

export const getApiKeys = async (): Promise<ApiKeyItem[]> => {
  const { data } = await apiClient.get<ApiKeyItem[]>("/api-keys");
  return data;
};

export const createApiKey = async (name: string): Promise<ApiKeyItem> => {
  const { data } = await apiClient.post<ApiKeyItem>("/api-keys", { name });
  return data;
};

export const revokeApiKey = async (id: string): Promise<void> => {
  await apiClient.delete(`/api-keys/${id}`);
};
