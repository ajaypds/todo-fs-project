import { create } from "zustand";

type Theme =
    | "light"
    | "dark";

type ThemeState = {

    theme: Theme;

    toggleTheme: () => void;
};

const storedTheme = localStorage.getItem("theme") as Theme | null;

export const useThemeStore =
    create<ThemeState>((set) => ({

        theme: storedTheme ?? "light",

        toggleTheme: () =>

            set((state) => {

                const nextTheme = state.theme === "light" ? "dark" : "light";

                localStorage.setItem("theme", nextTheme);

                return { theme: nextTheme };
            }),
    }));