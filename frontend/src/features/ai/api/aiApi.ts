import { apiClient } from "../../../api/client";

export const parseTask = async (input: string) => {

    const response = await apiClient.post(
        "/ai/parse-task",
        { input }
    );

    return response.data;
};