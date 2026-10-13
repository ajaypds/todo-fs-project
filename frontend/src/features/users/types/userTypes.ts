export type UserProfile = {
  id: string;
  email: string;
  fullName: string;
  timezone: string;
  createdAt?: string;
};

export type UpdateTimezonePayload = {
  timezone: string;
};
