import { useState } from "react";

import { useParseTask } from "../api/aiQueries";
import { Button } from "../../../components/ui/Button";

export const AiQuickAdd = () => {
  const [input, setInput] = useState("");
  const mutation = useParseTask();

  const handleParse = async () => {
    const result = await mutation.mutateAsync(input);

    console.log(result);
  };

  return (
    <div className="space-y-3 mb-4">
      <textarea
        value={input}
        onChange={(e) => setInput(e.target.value)}
        placeholder="Describe your task naturally..."
        className="
            w-full
            rounded-2xl
            border border-border
            p-4
            bg-card
          "
      />

      <Button variant="primary" className="px-10" onClick={handleParse}>
        Parse With AI
      </Button>
    </div>
  );
};
