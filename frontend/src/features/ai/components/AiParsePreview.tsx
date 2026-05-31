import { Button } from "../../../components/ui/Button";
import { Card } from "../../../components/ui/Card";
import { formatDate } from "../../../utils/date";
import type { ParsedTaskResponse } from "../types/aiTypes";

type Props = {
  parsedTask?: ParsedTaskResponse | null;
  onGenerate: (task: ParsedTaskResponse) => void;
};

const AiParsePreview = ({ parsedTask, onGenerate }: Props) => {
  return (
    <Card className="w-full p-4 mb-4">
      <h3 className="font-semibold">AI Preview</h3>
      <Card className="p-4 my-4">
        <div>
          <span className="font-semibold">Title:</span> {parsedTask?.title}
        </div>
        <div>
          <span className="font-semibold">Description:</span>{" "}
          {parsedTask?.description}
        </div>
        <div>
          <span className="font-semibold">Priority:</span>{" "}
          {parsedTask?.priority}
        </div>
        <div>
          <span className="font-semibold">Due Date:</span>{" "}
          {formatDate(parsedTask?.dueDate)}
        </div>
      </Card>
      {parsedTask && (
        <Button onClick={() => onGenerate(parsedTask)}>Apply to Form</Button>
      )}
    </Card>
  );
};

export default AiParsePreview;
