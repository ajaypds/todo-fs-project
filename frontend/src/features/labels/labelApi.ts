import { apiClient } from "../../api/client";
import type { Label } from "./labelTypes";


export const getLabels =
    async (): Promise<Label[]> => {

        const response =
            await apiClient.get(
                "/labels"
            );

        return response.data;
    };

export const createLabel =
    async (
        payload: {
            name: string;
            color: string;
        }
    ): Promise<Label> => {

        const response =
            await apiClient.post(
                "/labels",
                payload
            );

        return response.data;
    };