export type TaskRealtimeEvent = {

    type:
    | "TASK_CREATED"
    | "TASK_UPDATED"
    | "TASK_DELETED";

    taskId: string;

    title?: string;

    userId?: string;
};

export type PresenceRealtimeEvent = {

    type:
    | "USER_CONNECTED"
    | "USER_DISCONNECTED";

    username: string;

    userId?: string;

    activeSessions?: number;

    online?: boolean;
};

export type UserPresenceResponse = {
    userId: string;
    username: string;
    activeSessions: number;
    online: boolean;
};

export type ActivityRealtimeEvent = {

    type:
    | "ACTIVITY_CREATED";

    message: string;

    userId?: string;
};

export type RealtimeConnectionStatus = "connected" | "connecting" | "disconnected";