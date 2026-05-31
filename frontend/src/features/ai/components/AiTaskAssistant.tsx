import { useState } from "react";

import { useParseTask } from "../api/aiQueries";

import type { ParsedTaskResponse } from "../types/aiTypes";
import { Button } from "../../../components/ui/Button";
import { Card } from "../../../components/ui/Card";

type Props = {
  onParsed: (task: ParsedTaskResponse) => void;
};

export const AiTaskAssistant = ({ onParsed }: Props) => {
  const [input, setInput] = useState("");

  const mutation = useParseTask();

  const handleParse = async () => {
    const parsed = await mutation.mutateAsync(input);

    onParsed(parsed);
  };

  return (
    <Card className="p-4 mb-4">
      <h3 className="font-semibold">AI Assistant</h3>

      <textarea
        value={input}
        onChange={(e) => setInput(e.target.value)}
        placeholder="Example: Finish AWS deployment tomorrow high priority"
        className="
          w-full
          min-h-24
          rounded-xl
          border border-border
          p-3
        "
      />

      <Button
        onClick={handleParse}
        className={`${mutation.isPending ? "opacity-80" : ""}`}
        disabled={mutation.isPending}
      >
        {mutation.isPending ? "Thinking..." : "Generate Task"}
      </Button>
    </Card>
  );
};
