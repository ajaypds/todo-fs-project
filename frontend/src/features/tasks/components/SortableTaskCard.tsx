import { useSortable } from "@dnd-kit/sortable";

import { CSS } from "@dnd-kit/utilities";

import type { Task } from "../taskTypes";

import { TaskCard } from "./TaskCard";

type Props = {
  task: Task;

  onToggle: () => void;

  onDelete: () => void;

  onEdit: () => void;
};

export const SortableTaskCard = ({
  task,
  onToggle,
  onDelete,
  onEdit,
}: Props) => {
  const { attributes, listeners, setNodeRef, transform, transition } =
    useSortable({
      id: task.id,
    });

  const style = {
    transform: CSS.Transform.toString(transform),

    transition,
  };

  return (
    <div ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <TaskCard
        task={task}
        onToggle={onToggle}
        onDelete={onDelete}
        onEdit={onEdit}
      />
    </div>
  );
};
