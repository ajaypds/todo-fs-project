import { apiClient } from "../../api/client";

import type { Project } from "./projectTypes";

export type CreateProjectRequest = {
    name: string;

    color: string;
};

export const getProjects = async (): Promise<
    Project[]
> => {

    const response = await apiClient.get(
        "/projects"
    );

    return response.data;
};

export const createProject = async (
    request: CreateProjectRequest
): Promise<Project> => {

    const response = await apiClient.post(
        "/projects",
        request
    );

    return response.data;
};