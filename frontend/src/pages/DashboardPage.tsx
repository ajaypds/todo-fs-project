import { AppLayout } from "../components/layout/AppLayout";

import {
  useTasks,
  useCreateTask,
  useUpdateTask,
  useDeleteTask,
} from "../features/tasks/taskQueries";

import { TaskCard } from "../features/tasks/components/TaskCard";
import { CreateTaskForm } from "../features/tasks/components/CreateTaskForm";
import { EmptyState } from "../components/ui/EmptyState";
import { TaskSkeleton } from "../components/ui/TaskSkeleton";
import toast from "react-hot-toast";
import { useMemo, useState } from "react";

export const DashboardPage = () => {
  const { data, isLoading, error } = useTasks();

  const createTaskMutation = useCreateTask();

  const updateTaskMutation = useUpdateTask();

  const deleteTaskMutation = useDeleteTask();
  const [search, setSearch] = useState("");

  const tasks = useMemo(() => {
    return data?.content ?? [];
  }, [data]);

  const filteredTasks = useMemo(() => {
    return tasks.filter((task) =>
      task.title.toLowerCase().includes(search.toLowerCase()),
    );
  }, [tasks, search]);

  if (isLoading) {
    return (
      <AppLayout>
        <div className="max-w-3xl mx-auto space-y-3">
          <TaskSkeleton />
          <TaskSkeleton />
          <TaskSkeleton />
        </div>
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
            onCreate={(payload) => {
              //   createTaskMutation.mutate(payload);
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
        </div>

        <div className="mb-4">
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search tasks..."
            className="w-full border rounded-lg p-3 bg-white"
          />
        </div>

        {tasks.length === 0 ? (
          <EmptyState
            title="No tasks yet"
            description="Create your first task to get started."
          />
        ) : (
          <div className="space-y-3">
            {filteredTasks.map((task) => (
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
        )}
      </div>
    </AppLayout>
  );
};
