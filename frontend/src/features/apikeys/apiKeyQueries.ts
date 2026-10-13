import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getApiKeys, createApiKey, revokeApiKey, type ApiKeyItem } from "./apiKeyApi";
import toast from "react-hot-toast";

export const useApiKeys = (enabled = true) => {
  return useQuery({
    queryKey: ["apiKeys"],
    queryFn: getApiKeys,
    enabled,
  });
};

export const useCreateApiKey = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (name: string) => createApiKey(name),
    onSuccess: (newKey) => {
      queryClient.setQueryData<ApiKeyItem[]>(["apiKeys"], (old = []) => [newKey, ...old]);
      queryClient.invalidateQueries({ queryKey: ["apiKeys"] });
    },
    onError: () => {
      toast.error("Failed to generate API key");
    },
  });
};

export const useRevokeApiKey = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => revokeApiKey(id),
    onMutate: async (id: string) => {
      await queryClient.cancelQueries({ queryKey: ["apiKeys"] });
      const previous = queryClient.getQueryData<ApiKeyItem[]>(["apiKeys"]);
      queryClient.setQueryData<ApiKeyItem[]>(["apiKeys"], (old = []) =>
        old.filter((k) => k.id !== id)
      );
      return { previous };
    },
    onSuccess: () => {
      toast.success("API key revoked");
    },
    onError: (_err, _id, context) => {
      if (context?.previous) {
        queryClient.setQueryData(["apiKeys"], context.previous);
      }
      toast.error("Failed to revoke API key");
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["apiKeys"] });
    },
  });
};
