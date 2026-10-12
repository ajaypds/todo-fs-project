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

export type VerifyEmailRequest = {
    email: string;
    code: string;
};

export type ResendVerificationRequest = {
    email: string;
};

export type AuthResponse = {
    token?: string;
    refreshToken?: string;
    emailVerificationRequired?: boolean;
    email?: string;
    message?: string;
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

export const verifyEmail = async (request: VerifyEmailRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>(
        "/auth/verify-email",
        request
    );

    return response.data;
};

export const resendVerification = async (request: ResendVerificationRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>(
        "/auth/resend-verification",
        request
    );

    return response.data;
};