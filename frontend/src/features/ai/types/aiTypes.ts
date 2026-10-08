export type ParsedTaskResponse = {

    title: string;

    description: string;

    priority: 1 | 2 | 3 | 4;

    dueDate: string | null;
};

export type DecomposeTaskRequest = {
    title: string;
    description?: string;
};

export type DecomposedSubtaskItem = {
    title: string;
};

export type DecomposeTaskResponse = {
    subtasks: DecomposedSubtaskItem[];
};

export type DailyPlannerTaskSnapshot = {
    id: string;
    title: string;
    description?: string;
    priority: number;
    dueDate?: string;
    projectName?: string;
    subtaskCount?: number;
    completedSubtaskCount?: number;
};

export type DailyPlannerRequest = {
    tasks: DailyPlannerTaskSnapshot[];
    userTimezone?: string;
};

export type MatrixTaskItem = {
    taskId: string;
    title: string;
    rationale: string;
    urgencyScore?: number;
    importanceScore?: number;
};

export type EisenhowerMatrix = {
    doFirst: MatrixTaskItem[];
    schedule: MatrixTaskItem[];
    delegate: MatrixTaskItem[];
    eliminate: MatrixTaskItem[];
};

export type DailyTimeBlock = {
    timePeriod: string;
    focusTheme: string;
    taskIds: string[];
    taskTitles: string[];
};

export type DailyPlannerResponse = {
    summary: string;
    coachingTip: string;
    timeBlocks: DailyTimeBlock[];
    matrix: EisenhowerMatrix;
};