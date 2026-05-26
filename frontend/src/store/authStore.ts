import { create } from "zustand";
import { authStorage } from "../features/auth/authStorage";

type AuthState = {
    token: string | null;
    refreshToken: string | null;

    setToken: (token: string) => void;
    setAuth: (token: string, refreshToken: string) => void;

    logout: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
    token: authStorage.getAccessToken(),
    refreshToken: authStorage.getRefreshToken(),

    setToken: (token) => {
        // localStorage.setItem("token", token);
        authStorage.setAccessToken(token);
        set({ token });
    },

    logout: () => {
        // localStorage.removeItem("token");
        authStorage.removeAccessToken();
        authStorage.removeRefreshToken();
        set({ token: null, refreshToken: null });
    },

    setAuth: (token, refreshToken) => {
        // localStorage.setItem("token", token);
        // localStorage.setItem("refreshToken", refreshToken);
        authStorage.setAccessToken(token);
        authStorage.setRefreshToken(refreshToken);
        set({ token, refreshToken });
    },
}));