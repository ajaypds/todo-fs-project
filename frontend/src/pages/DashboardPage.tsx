import { AppLayout } from "../components/layout/AppLayout";

import {
  useTasks,
  useCreateTask,
  useUpdateTask,
  useDeleteTask,
  useReorderTasks,
} from "../features/tasks/api/taskQueries";
import type { PageResponse } from "../types/pagination";
import { CreateTaskForm } from "../features/tasks/components/CreateTaskForm";
import { EmptyState } from "../components/ui/EmptyState";
import { TaskSkeleton } from "../components/ui/TaskSkeleton";
import toast from "react-hot-toast";
import { useEffect, useMemo, useState } from "react";
import {
  useCreateProject,
  useProjects,
} from "../features/projects/projectQueries";
import { CreateProjectForm } from "../features/projects/components/CreateProjectForm";
import { useProjectStore } from "../store/projectStore";
import { Modal } from "../components/ui/Modal";
import { EditTaskForm } from "../features/tasks/components/EditTaskForm";
import type { Task } from "../features/tasks/types/taskTypes";
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
import { Plus } from "lucide-react";
import { PageTransition } from "../components/ui/PageTransition";
import { useLabels } from "../features/labels/labelQueries";
import { OnlineUsers } from "../features/users/components/OnlineUsers";
import { ActivityFeed } from "../features/activity/components/ActivityFeed";
import { useOnlineUsers } from "../features/realtime/api/presenceQueries";
import { usePresenceStore } from "../store/presenceStore";
// import { AiQuickAdd } from "../features/ai/components/AiQuickAdd";
import type { ParsedTaskResponse } from "../features/ai/types/aiTypes";
import { AiTaskAssistant } from "../features/ai/components/AiTaskAssistant";
import AiParsePreview from "../features/ai/components/AiParsePreview";

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
  const [showQuickAdd, setShowQuickAdd] = useState(false);
  const { data: onlineUsers = [] } = useOnlineUsers();
  const setUsers = usePresenceStore((state) => state.setUsers);
  const reorderTasksMutation = useReorderTasks();
  const queryClient = useQueryClient();
  const { data: labels = [] } = useLabels();
  const [aiTask, setAiTask] = useState<ParsedTaskResponse | null>(null);
  const [formData, setFormData] = useState<ParsedTaskResponse | null>(null);

  const tasks = useMemo(() => {
    return data?.content ?? [];
  }, [data]);

  const handleDragStart = (event: DragStartEvent) => {
    setActiveId(event.active.id as string);
  };

  const onGenerate = (task: ParsedTaskResponse) => {
    setFormData(task);
  };

  useEffect(() => {
    console.log("Online users updated (dashboard):", onlineUsers);
    setUsers(onlineUsers);
  }, [onlineUsers, setUsers]);

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

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "n" && !event.metaKey && !event.ctrlKey) {
        // setShowQuickAdd(true);
      }
    };

    window.addEventListener("keydown", handleKeyDown);

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

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
      <PageTransition>
        <OnlineUsers />
        <div className="max-w-4xl mx-auto px-4 md:px-6 py-6 md:py-8">
          <h1 className="text-3xl font-bold mb-6">Inbox</h1>

          <div className="mb-6">
            <CreateProjectForm
              onCreate={(payload) => {
                createProjectMutation.mutate(payload);
              }}
            />
            <AiTaskAssistant onParsed={setAiTask} />
            {aiTask && (
              <AiParsePreview parsedTask={aiTask} onGenerate={onGenerate} />
            )}
            <CreateTaskForm
              projects={projects}
              labels={labels}
              parsedTask={formData}
              // initialTitle={aiTask?.title}
              // initialDescription={aiTask?.description}
              // initialPriority={aiTask?.priority ?? 1}
              // initialDueDate={aiTask?.dueDate ?? undefined}
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

          {/* <AiQuickAdd /> */}

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
          <ActivityFeed />
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
        <Modal
          open={showQuickAdd}
          title="Quick Add Task"
          onClose={() => setShowQuickAdd(false)}
        >
          <CreateTaskForm
            projects={projects}
            labels={labels}
            onCreate={(payload) => {
              createTaskMutation.mutate(payload, {
                onSuccess: () => {
                  setShowQuickAdd(false);
                },
              });
            }}
          />
        </Modal>
        <button
          className="
            fixed bottom-6 right-6 md:bottom-8 md:right-8
            w-14 h-14 rounded-full
            bg-accent text-white
            shadow-card
            flex items-center justify-center
            hover:scale-105
            hover:cursor-pointer
            transition-all
            duration-200
          "
          onClick={() => {
            setShowQuickAdd(true);
          }}
        >
          <Plus size={24} />
        </button>
      </PageTransition>
    </AppLayout>
  );
};
