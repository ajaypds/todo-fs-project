import Drawer from "../../../components/ui/Drawer";

import AiTaskAssistant from "./AiTaskAssistant";

import AiParsePreview from "./AiParsePreview";

import type { ParsedTaskResponse } from "../types/aiTypes";

type Props = {
  open: boolean;

  onClose: () => void;

  parsedTask: ParsedTaskResponse | null;

  onParsed: (task: ParsedTaskResponse) => void;

  onApply: (task: ParsedTaskResponse) => void;
};

const AiAssistantDrawer = (props: Props) => {
  return (
    <Drawer open={props.open} onClose={props.onClose} title="AI Assistant">
      <AiTaskAssistant onParsed={props.onParsed} />

      <AiParsePreview
        parsedTask={props.parsedTask}
        onGenerate={props.onApply}
      />
    </Drawer>
  );
};

export default AiAssistantDrawer;
