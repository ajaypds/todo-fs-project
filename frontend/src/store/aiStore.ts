import { create } from "zustand";
import type { ParsedTaskResponse } from "../features/ai/types/aiTypes";

import type { ProductivityInsightResponse } from "../features/ai/types/productivityTypes";

type AiState = {

    parsedTask: ParsedTaskResponse | null;

    productivityInsights: ProductivityInsightResponse | null;

    setParsedTask: (task: ParsedTaskResponse | null) => void;

    setProductivityInsights: (insights: ProductivityInsightResponse | null) => void;

    clearParsedTask: () => void;

    clearInsights: () => void;
};

export const useAiStore =
    create<AiState>((set) => ({

        parsedTask: null,

        productivityInsights: null,

        setParsedTask: (
            task
        ) =>
            set({
                parsedTask: task,
            }),

        setProductivityInsights: (
            insights
        ) =>
            set({
                productivityInsights:
                    insights,
            }),

        clearParsedTask: () =>
            set({
                parsedTask: null,
            }),

        clearInsights: () =>
            set({
                productivityInsights:
                    null,
            }),
    }));