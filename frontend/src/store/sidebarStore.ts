import { create } from "zustand";

type SidebarState = {
  open: boolean;
  desktopCollapsed: boolean;
  setOpen: (open: boolean) => void;
  toggle: () => void;
  toggleDesktopCollapsed: () => void;
  setDesktopCollapsed: (collapsed: boolean) => void;
};

const getInitialDesktopCollapsed = (): boolean => {
  if (typeof window === "undefined") return false;
  try {
    return localStorage.getItem("todoflow_sidebar_collapsed") === "true";
  } catch {
    return false;
  }
};

export const useSidebarStore = create<SidebarState>((set) => ({
  open: false,
  desktopCollapsed: getInitialDesktopCollapsed(),

  setOpen: (open) => set({ open }),

  toggle: () =>
    set((state) => ({
      open: !state.open,
    })),

  toggleDesktopCollapsed: () =>
    set((state) => {
      const next = !state.desktopCollapsed;
      try {
        localStorage.setItem("todoflow_sidebar_collapsed", String(next));
      } catch {
        // ignore
      }
      return { desktopCollapsed: next };
    }),

  setDesktopCollapsed: (collapsed) => {
    try {
      localStorage.setItem("todoflow_sidebar_collapsed", String(collapsed));
    } catch {
      // ignore
    }
    set({ desktopCollapsed: collapsed });
  },
}));