import { apiClient } from "../../../api/client";
import type { ParsedTaskResponse } from "../types/aiTypes";

export const parseTask = async (input: string) => {

    const response = await apiClient.post(
        "/ai/parse-task",
        { input }
    );

    return response.data as ParsedTaskResponse;
};