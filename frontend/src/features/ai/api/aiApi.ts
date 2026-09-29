import { apiClient } from "../../../api/client";
import type { DecomposeTaskRequest, DecomposeTaskResponse, ParsedTaskResponse } from "../types/aiTypes";

export const parseTask = async (input: string) => {

    const response = await apiClient.post(
        "/ai/parse-task",
        { input }
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