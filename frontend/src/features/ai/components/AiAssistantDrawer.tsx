import Drawer from "../../../components/ui/Drawer";
import AiTaskAssistant from "./AiTaskAssistant";
import AiParsePreview from "./AiParsePreview";
import type { ParsedTaskResponse } from "../types/aiTypes";
import { useAiStore } from "../../../store/aiStore";

type Props = {
  open: boolean;
  onClose: () => void;
  onApply: (task: ParsedTaskResponse) => void;
};

export const AiAssistantDrawer = ({ open, onClose, onApply }: Props) => {
  const parsedTask = useAiStore((state) => state.parsedTask);
  const setParsedTask = useAiStore((state) => state.setParsedTask);

  return (
    <Drawer open={open} onClose={onClose} title="✨ AI Task Assistant">
      <div className="space-y-4">
        <p className="text-xs text-muted">
          Describe what you need to do in natural language. The AI will extract the task title, description, priority, and due date for review.
        </p>

        <AiTaskAssistant onParsed={setParsedTask} />

        {parsedTask && (
          <AiParsePreview
            parsedTask={parsedTask}
            onGenerate={(task) => {
              onApply(task);
            }}
          />
        )}
      </div>
    </Drawer>
  );
};

export default AiAssistantDrawer;

