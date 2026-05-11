import { AppLayout } from "../components/layout/AppLayout";

import {
  useTasks,
  useCreateTask,
  useUpdateTask,
  useDeleteTask,
} from "../features/tasks/taskQueries";

import { TaskCard } from "../features/tasks/components/TaskCard";
import { CreateTaskForm } from "../features/tasks/components/CreateTaskForm";
import type { Task } from "../features/tasks/taskTypes";
import toast from "react-hot-toast";

export const DashboardPage = () => {
  const { data, isLoading, error } = useTasks();

  const createTaskMutation = useCreateTask();

  const updateTaskMutation = useUpdateTask();

  const deleteTaskMutation = useDeleteTask();

  const tasks = data?.content ?? [];

  if (isLoading) {
    return (
      <AppLayout>
        <div>Loading tasks...</div>
      </AppLayout>
    );
  }

  if (error) {
    return (
      <AppLayout>
        <div>Failed to load tasks</div>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <div className="max-w-3xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">Inbox</h1>

        <div className="mb-6">
          <CreateTaskForm
            onCreate={(title, description) => {
              createTaskMutation.mutate(
                {
                  title,
                  description,
                },
                {
                  onSuccess: () => {
                    toast.success("Task created");
                  },

                  onError: () => {
                    toast.error("Failed to create task");
                  },
                },
              );
            }}
          />
        </div>

        <div className="space-y-3">
          {tasks.map((task: Task) => (
            <TaskCard
              key={task.id}
              task={task}
              onToggle={() => {
                updateTaskMutation.mutate(
                  {
                    taskId: task.id,

                    payload: {
                      completed: !task.completed,
                    },
                  },
                  {
                    onSuccess: () => {
                      toast.success("Task updated");
                    },

                    onError: () => {
                      toast.error("Failed to update task");
                    },
                  },
                );
              }}
              onDelete={() => {
                deleteTaskMutation.mutate(task.id, {
                  onSuccess: () => {
                    toast.success("Task deleted");
                  },

                  onError: () => {
                    toast.error("Failed to delete task");
                  },
                });
              }}
            />
          ))}
        </div>
      </div>
    </AppLayout>
  );
};
