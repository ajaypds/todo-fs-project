import Modal from "../../../components/ui/Modal";
import type { ParsedTaskResponse } from "../../ai/types/aiTypes";
import type { Label } from "../../labels/labelTypes";
import type { Project } from "../../projects/projectTypes";
import { useCreateTask } from "../api/taskQueries";
import toast from "react-hot-toast";
import CreateTaskForm from "./CreateTaskForm";
import { useAiStore } from "../../../store/aiStore";

type Props = {
  open: boolean;
  onClose: () => void;
  projects: Project[];
  labels: Label[];
  parsedTask?: ParsedTaskResponse | undefined | null;
};

export const CreateTaskModal = (props: Props) => {
  const createTaskMutation = useCreateTask();
  const storeParsedTask = useAiStore((state) => state.parsedTask);
  const clearParsedTask = useAiStore((state) => state.clearParsedTask);

  const activeParsedTask = props.parsedTask ?? storeParsedTask;

  const handleClose = () => {
    props.onClose();
  };

  return (
    <Modal open={props.open} onClose={handleClose} title="Create Task">
      <CreateTaskForm
        projects={props.projects}
        labels={props.labels}
        parsedTask={activeParsedTask}
        onCreate={(payload) => {
          createTaskMutation.mutate(payload, {
            onSuccess: () => {
              toast.success("Task created");
              clearParsedTask();
              props.onClose();
            },
            onError: () => {
              toast.error("Failed to create task");
            },
          });
        }}
      />
    </Modal>
  );
};

export default CreateTaskModal;

