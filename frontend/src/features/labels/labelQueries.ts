import {
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query";

import {
    createLabel,
    getLabels,
} from "./labelApi";

export const useLabels = () => {

    return useQuery({
        queryKey: ["labels"],
        queryFn: getLabels,
    });
};

export const useCreateLabel = () => {

    const queryClient = useQueryClient();

    return useMutation({

        mutationFn: createLabel,

        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ["labels"],
            });
        },
    });
};