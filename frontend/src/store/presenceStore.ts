import { create } from "zustand";

type PresenceState = {

    users: string[];

    setUsers: (
        users: string[]
    ) => void;

    addUser: (
        username: string
    ) => void;

    removeUser: (
        username: string
    ) => void;
};

export const usePresenceStore = create<PresenceState>((set) => ({

    users: [],

    setUsers: (users) => set({ users }),

    addUser: (username) => set((state) => ({

        users: [
            ...new Set([...state.users, username]),
        ],
    })),

    removeUser: (username) =>

        set((state) => ({

            users:
                state.users.filter(
                    (user) => user !== username
                ),
        })),
}));