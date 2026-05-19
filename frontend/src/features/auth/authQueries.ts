import { useMutation } from "@tanstack/react-query";
import { login, register } from "./authApi";
import type { LoginRequest, RegisterRequest } from "./authApi";

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