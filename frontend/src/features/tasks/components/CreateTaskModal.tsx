import Modal from "../../../components/ui/Modal";
import type { ParsedTaskResponse } from "../../ai/types/aiTypes";
import type { Label } from "../../labels/labelTypes";
import type { Project } from "../../projects/projectTypes";
import { useCreateTask } from "../api/taskQueries";
import toast from "react-hot-toast";
import CreateTaskForm from "./CreateTaskForm";

type Props = {
  open: boolean;
  onClose: () => void;
  projects: Project[];
  labels: Label[];
  parsedTask?: ParsedTaskResponse | undefined | null;
};

const CreateTaskModal = (props: Props) => {
  const createTaskMutation = useCreateTask();
  return (
    <Modal open={props.open} onClose={props.onClose} title="Create Task">
      <CreateTaskForm
        projects={props.projects}
        labels={props.labels}
        parsedTask={props.parsedTask}
        onCreate={(payload) => {
          createTaskMutation.mutate(payload, {
            onSuccess: () => {
              toast.success("Task created");
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
