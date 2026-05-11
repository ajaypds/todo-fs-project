import { apiClient } from "../../api/client";

export type LoginRequest = {
    email: string;
    password: string;
}

export type RegisterRequest = {
    email: string;
    password: string;
    fullName: string;
}

export const login = async (request: LoginRequest) => {
    const response = await apiClient.post(
        "/auth/login",
        request
    );

    return response.data;
}

export const register = async (request: RegisterRequest) => {

    const response = await apiClient.post(
        "/auth/register",
        request);

    return response.data;
}