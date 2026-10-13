import type { Label } from "../../labels/labelTypes";

export type Subtask = {
  id: string;
  taskId: string;
  title: string;
  completed: boolean;
  position: number;
  createdAt: string;
};

export type RecurrenceType = "NONE" | "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";

export type Task = {
  id: string;
  title: string;
  description?: string;
  completed: boolean;
  priority: number;
  dueDate?: string;
  recurrenceType?: RecurrenceType;
  recurrenceInterval?: number;
  createdAt: string;
  updatedAt: string;
  projectId: string;
  labels?: Label[];
  subtasks?: Subtask[];
  optimistic?: boolean;
};