import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getCurrentUser, updateTimezone } from "./userApi";
import type { UpdateTimezonePayload } from "../types/userTypes";

export const useCurrentUser = (enabled = true) => {
  return useQuery({
    queryKey: ["currentUser"],
    queryFn: getCurrentUser,
    enabled,
    staleTime: 1000 * 60 * 15,
  });
};

export const useUpdateTimezone = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: UpdateTimezonePayload) => updateTimezone(payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(["currentUser"], updated);
    },
  });
};
