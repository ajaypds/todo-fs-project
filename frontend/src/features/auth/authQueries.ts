import { useMutation } from "@tanstack/react-query";
import { login, register, verifyEmail, resendVerification } from "./authApi";
import type { LoginRequest, RegisterRequest, VerifyEmailRequest, ResendVerificationRequest } from "./authApi";

export const useLogin = () => {
    return useMutation({
        mutationFn: (payload: LoginRequest) => login(payload),
    });
};

export const useRegister = () => {
    return useMutation({
        mutationFn: (payload: RegisterRequest) => register(payload),
    });
};

export const useVerifyEmail = () => {
    return useMutation({
        mutationFn: (payload: VerifyEmailRequest) => verifyEmail(payload),
    });
};

export const useResendVerification = () => {
    return useMutation({
        mutationFn: (payload: ResendVerificationRequest) => resendVerification(payload),
    });
};