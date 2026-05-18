import { AppLayout } from "../components/layout/AppLayout";

import {
  useTasks,
  useCreateTask,
  useUpdateTask,
  useDeleteTask,
  useReorderTasks,
} from "../features/tasks/taskQueries";
import type { PageResponse } from "../types/pagination";

// import { TaskCard } from "../features/tasks/components/TaskCard";
import { CreateTaskForm } from "../features/tasks/components/CreateTaskForm";
import { EmptyState } from "../components/ui/EmptyState";
import { TaskSkeleton } from "../components/ui/TaskSkeleton";
import toast from "react-hot-toast";
import { useMemo, useState } from "react";
import {
  useCreateProject,
  useProjects,
} from "../features/projects/projectQueries";
import { CreateProjectForm } from "../features/projects/components/CreateProjectForm";
import { useProjectStore } from "../store/projectStore";
import { Modal } from "../components/ui/Modal";
import { EditTaskForm } from "../features/tasks/components/EditTaskForm";
import type { Task } from "../features/tasks/taskTypes";
import type { DragStartEvent } from "@dnd-kit/core";
import { DndContext, closestCenter, DragOverlay } from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import type { DragEndEvent } from "@dnd-kit/core";
import { SortableTaskCard } from "../features/tasks/components/SortableTaskCard";
import { useQueryClient } from "@tanstack/react-query";
import { TaskCard } from "../features/tasks/components/TaskCard";
import { Input } from "../components/ui/Input";

export const DashboardPage = () => {
  const { data, isLoading, error } = useTasks();

  const createTaskMutation = useCreateTask();

  const updateTaskMutation = useUpdateTask();

  const deleteTaskMutation = useDeleteTask();
  const [search, setSearch] = useState("");

  const createProjectMutation = useCreateProject();
  const selectedProjectId = useProjectStore((state) => state.selectedProjectId);
  const { data: projects = [] } = useProjects();
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);

  const tasks = useMemo(() => {
    return data?.content ?? [];
  }, [data]);

  const handleDragStart = (event: DragStartEvent) => {
    setActiveId(event.active.id as string);
  };

  const reorderTasksMutation = useReorderTasks();
  const queryClient = useQueryClient();

  const filteredTasks = useMemo(() => {
    return tasks.filter((task) => {
      const matchesSearch = task.title
        .toLowerCase()
        .includes(search.toLowerCase());

      const matchesProject =
        !selectedProjectId || task.projectId === selectedProjectId;

      return matchesSearch && matchesProject;
    });
  }, [tasks, search, selectedProjectId]);

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveId(null);
    const { active, over } = event;

    if (!over || active.id === over.id) {
      return;
    }

    const oldIndex = filteredTasks.findIndex((task) => task.id === active.id);

    const newIndex = filteredTasks.findIndex((task) => task.id === over.id);

    const reorderedTasks = arrayMove(filteredTasks, oldIndex, newIndex);

    queryClient.setQueryData(
      ["tasks"],
      (old: PageResponse<Task> | undefined) => {
        if (!old) {
          return old;
        }

        return {
          ...old,
          content: reorderedTasks,
        };
      },
    );

    reorderTasksMutation.mutate(reorderedTasks.map((task) => task.id));
  };

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
  const activeTask = filteredTasks.find((task) => task.id === activeId);

  return (
    <AppLayout>
      <div className="max-w-4xl mx-auto px-6 py-8">
        <h1 className="text-3xl font-bold mb-6">Inbox</h1>

        <div className="mb-6">
          <CreateProjectForm
            onCreate={(payload) => {
              createProjectMutation.mutate(payload);
            }}
          />
          <CreateTaskForm
            projects={projects}
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
        </div>

        <div className="mb-4">
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search tasks..."
            // className="w-full border rounded-lg p-3"
          />
        </div>

        {tasks.length === 0 ? (
          <EmptyState
            title="No tasks yet"
            description="Create your first task to get started."
          />
        ) : (
          <DndContext
            collisionDetection={closestCenter}
            onDragStart={handleDragStart}
            onDragEnd={handleDragEnd}
          >
            <SortableContext
              items={filteredTasks.map((task) => task.id)}
              strategy={verticalListSortingStrategy}
            >
              <div className="space-y-3">
                {filteredTasks.map((task) => (
                  <SortableTaskCard
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
                    onEdit={() => {
                      setEditingTask(task);
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
            </SortableContext>
            <DragOverlay>
              {activeTask ? (
                <TaskCard
                  task={activeTask}
                  onToggle={() => {}}
                  onDelete={() => {}}
                  onEdit={() => {}}
                  className="cursor-grabbing opacity-80"
                />
              ) : null}
            </DragOverlay>
          </DndContext>
        )}
      </div>
      <Modal
        open={!!editingTask}
        title="Edit Task"
        onClose={() => setEditingTask(null)}
      >
        {editingTask && (
          <EditTaskForm
            task={editingTask}
            onSave={(payload) => {
              updateTaskMutation.mutate(
                {
                  taskId: editingTask.id,

                  payload,
                },

                {
                  onSuccess: () => {
                    toast.success("Task updated");

                    setEditingTask(null);
                  },

                  onError: () => {
                    toast.error("Failed to update task");
                  },
                },
              );
            }}
          />
        )}
      </Modal>
    </AppLayout>
  );
};
