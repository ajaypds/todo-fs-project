import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { Task } from "../taskTypes";
import { TaskCard } from "./TaskCard";
import { GripVertical } from "lucide-react";

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
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: task.id });

  const style = {
    transform: CSS.Transform.toString(transform) || undefined,
    transition: transition || undefined,
    opacity: isDragging ? 0 : 1,
  };

  return (
    <div ref={setNodeRef} style={style} className="group relative">
      <div className="flex items-center gap-2">
        {/* Drag handle - only this can trigger drag */}
        <div
          {...attributes}
          {...listeners}
          className="absolute -left-4 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity duration-150 pointer-events-none group-hover:pointer-events-auto hover:cursor-grab shrink-0 "
          title="Drag to reorder"
        >
          <GripVertical size={18} className="text-muted" />
        </div>

        {/* TaskCard - all controls work normally */}
        <div className="flex-1">
          <TaskCard
            task={task}
            onToggle={onToggle}
            onDelete={onDelete}
            onEdit={onEdit}
          />
        </div>
      </div>
    </div>
  );
};
