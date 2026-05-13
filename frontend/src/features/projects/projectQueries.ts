import {
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query";

import {
    createProject,
    getProjects,
} from "./projectApi";

export const useProjects = () => {

    return useQuery({
        queryKey: ["projects"],

        queryFn: getProjects,
    });
};

export const useCreateProject = () => {

    const queryClient = useQueryClient();

    return useMutation({

        mutationFn: createProject,

        onSuccess: () => {

            queryClient.invalidateQueries({
                queryKey: ["projects"],
            });
        },
    });
};