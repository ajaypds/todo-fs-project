import { apiClient } from "../../../api/client";
import type { UserProfile, UpdateTimezonePayload } from "../types/userTypes";

export const getCurrentUser = async (): Promise<UserProfile> => {
  const response = await apiClient.get<UserProfile>("/users/me");
  return response.data;
};

export const updateTimezone = async (
  payload: UpdateTimezonePayload
): Promise<UserProfile> => {
  const response = await apiClient.patch<UserProfile>(
    "/users/me/timezone",
    payload
  );
  return response.data;
};
