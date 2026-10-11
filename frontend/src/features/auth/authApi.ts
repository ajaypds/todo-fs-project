import { apiClient } from "../../api/client";

export type LoginRequest = {
    email: string;
    password: string;
};

export type RegisterRequest = {
    email: string;
    password: string;
    fullName: string;
    timezone?: string;
};

export type AuthResponse = {
    token: string;
    refreshToken: string;
};

export const login = async (request: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>(
        "/auth/login",
        request
    );

    return response.data;
};

export const register = async (request: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>(
        "/auth/register",
        request
    );

    return response.data;
};