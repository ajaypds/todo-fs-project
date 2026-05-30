import { useMutation } from "@tanstack/react-query";

import { parseTask } from "./aiApi";

export const useParseTask = () => {

    return useMutation({
        mutationFn: parseTask,
    });
};