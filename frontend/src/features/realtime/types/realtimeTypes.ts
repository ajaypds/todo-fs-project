export type TaskRealtimeEvent = {

    type:
    | "TASK_CREATED"
    | "TASK_UPDATED"
    | "TASK_DELETED";

    taskId: string;

    title?: string;
};

export type PresenceRealtimeEvent = {

    type:
    | "USER_CONNECTED"
    | "USER_DISCONNECTED";

    username: string;
};

export type ActivityRealtimeEvent = {

    type:
    | "ACTIVITY_CREATED";

    message: string;
};