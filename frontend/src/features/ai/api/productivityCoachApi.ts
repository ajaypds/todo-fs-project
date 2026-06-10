import { apiClient } from "../../../api/client";

import type { ProductivityInsightResponse } from "../types/productivityTypes";

export const getInsights = async (tasks: unknown[]): Promise<ProductivityInsightResponse> => {

    const response = await apiClient.post(
        "/ai/productivity-coach",
        { tasks }
    );

    return response.data;
};