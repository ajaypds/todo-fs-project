import { Modal } from "../../../components/ui/Modal";
import { AiProductivityCoach } from "./AiProductivityCoach";
import type { Task } from "../../tasks/types/taskTypes";

type Props = {
  open: boolean;
  onClose: () => void;
  tasks: Task[];
};

export const AiInsightsModal = ({ open, onClose, tasks }: Props) => {
  return (
    <Modal open={open} onClose={onClose} title="AI Productivity Coach">
      <AiProductivityCoach tasks={tasks} />
    </Modal>
  );
};
