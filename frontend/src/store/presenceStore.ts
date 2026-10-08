import { create } from "zustand";

type PresenceState = {
    users: string[];
    online: boolean;
    activeSessions: number;
    username: string | null;

    setPresence: (presence: {
        online?: boolean;
        activeSessions?: number;
        username?: string;
    }) => void;

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
    online: false,
    activeSessions: 0,
    username: null,

    setPresence: (presence) =>
        set((state) => {
            const online = presence.online ?? state.online;
            const activeSessions = presence.activeSessions ?? state.activeSessions;
            const username = presence.username ?? state.username;
            const users = online && username ? [username] : [];
            return {
                online,
                activeSessions,
                username,
                users,
            };
        }),

    setUsers: (users) =>
        set({
            users,
            online: users.length > 0,
            activeSessions: users.length > 0 ? 1 : 0,
        }),

    addUser: (username) =>
        set((state) => ({
            users: [
                ...new Set([...state.users, username]),
            ],
            online: true,
            activeSessions: Math.max(state.activeSessions, 1),
            username,
        })),

    removeUser: (username) =>
        set((state) => {
            const users = state.users.filter(
                (user) => user !== username
            );
            return {
                users,
                online: users.length > 0,
                activeSessions: users.length > 0 ? state.activeSessions : 0,
            };
        }),
}));