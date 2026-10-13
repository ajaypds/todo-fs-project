import { apiClient } from "../../../api/client";
import { getUserTimezone } from "../../../utils/date";
import type {
  DecomposeTaskRequest,
  DecomposeTaskResponse,
  ParsedTaskResponse,
  DailyPlannerRequest,
  DailyPlannerResponse,
} from "../types/aiTypes";

export const parseTask = async (input: string, timezone?: string) => {
    const tz = timezone || getUserTimezone();
    const response = await apiClient.post(
        "/ai/parse-task",
        { input, timezone: tz }
    );

    return response.data as ParsedTaskResponse;
};

export const decomposeTask = async (payload: DecomposeTaskRequest) => {
    const response = await apiClient.post(
        "/ai/decompose-task",
        payload
    );

    return response.data as DecomposeTaskResponse;
};

export const generateDailyPlan = async (payload: DailyPlannerRequest) => {
    const response = await apiClient.post(
        "/ai/daily-planner",
        payload
    );

    return response.data as DailyPlannerResponse;
};