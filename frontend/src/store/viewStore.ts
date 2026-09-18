import { create } from "zustand";

export type ViewType = "inbox" | "today" | "upcoming" | "overdue";

type ViewState = {
  activeView: ViewType;
  selectedProjectId: string | null;
  setActiveView: (view: ViewType) => void;
  setSelectedProjectId: (id: string | null) => void;
  resetView: () => void;
};

export const useViewStore = create<ViewState>((set) => ({
  activeView: "inbox",
  selectedProjectId: null,

  setActiveView: (view) =>
    set({
      activeView: view,
      selectedProjectId: null,
    }),

  setSelectedProjectId: (id) =>
    set((state) => ({
      selectedProjectId: id,
      activeView: id ? "inbox" : state.activeView,
    })),

  resetView: () =>
    set({
      activeView: "inbox",
      selectedProjectId: null,
    }),
}));
