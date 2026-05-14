import { create } from "zustand";

type AuthState = {
    token: string | null;
    refreshToken: string | null;

    setToken: (token: string) => void;
    setAuth: (token: string, refreshToken: string) => void;

    logout: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
    token: localStorage.getItem("token"),
    refreshToken: localStorage.getItem("refreshToken"),

    setToken: (token) => {
        localStorage.setItem("token", token);
        set({ token });
    },

    logout: () => {
        localStorage.removeItem("token");
        set({ token: null, refreshToken: null });
    },

    setAuth: (token, refreshToken) => {
        localStorage.setItem("token", token);
        localStorage.setItem("refreshToken", refreshToken);
        set({ token, refreshToken });
    },
}));