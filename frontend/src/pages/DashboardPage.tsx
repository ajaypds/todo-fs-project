import { useEffect, useMemo, useState } from "react";
import { AppLayout } from "../components/layout/AppLayout";
import {
  useTasks,
  useUpdateTask,
  useDeleteTask,
  useReorderTasks,
} from "../features/tasks/api/taskQueries";
import { EmptyState } from "../components/ui/EmptyState";
import { TaskSkeleton } from "../components/ui/TaskSkeleton";
import toast from "react-hot-toast";
import { useViewStore } from "../store/viewStore";
import { useCommandPaletteStore } from "../store/commandPaletteStore";
import type { DragStartEvent, DragEndEvent } from "@dnd-kit/core";
import { DndContext, closestCenter, DragOverlay } from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { SortableTaskCard } from "../features/tasks/components/SortableTaskCard";
import { TaskCard } from "../features/tasks/components/TaskCard";
import { Input } from "../components/ui/Input";
import {
  Plus,
  Sparkles,
  Brain,
  Search,
  Inbox,
  CalendarDays,
  Calendar,
  AlertCircle,
  Command,
  CalendarCheck,
  Activity,
  Trash2,
} from "lucide-react";
import { PageTransition } from "../components/ui/PageTransition";
import { useLabels } from "../features/labels/labelQueries";
import { OnlineUsers } from "../features/users/components/OnlineUsers";
import { ActivityDrawer } from "../features/activity/components/ActivityDrawer";
import { useOnlineUsers } from "../features/realtime/api/presenceQueries";
import { usePresenceStore } from "../store/presenceStore";
import { useAiStore } from "../store/aiStore";
import { useDailyPlannerStore } from "../store/dailyPlannerStore";
import { Button } from "../components/ui/Button";
import { Modal } from "../components/ui/Modal";
import { useProjects, useDeleteProject } from "../features/projects/projectQueries";
import type { Project } from "../features/projects/projectTypes";
import { AiInsightsModal } from "../features/ai/components/AiInsightsModal";
import AiAssistantDrawer from "../features/ai/components/AiAssistantDrawer";
import AiDailyPlannerModal from "../features/ai/components/AiDailyPlannerModal";
import CreateTaskModal from "../features/tasks/components/CreateTaskModal";
import TaskDetailsDrawer from "../features/tasks/components/TaskDetailsDrawer";
import CreateProjectModal from "../features/projects/components/CreateProjectModal";
import ApiKeysModal from "../features/apikeys/components/ApiKeysModal";
import CommandPalette from "../components/ui/CommandPalette";
import { isToday, isUpcoming, isOverdue } from "../utils/date";

export const DashboardPage = () => {
  const { data, isLoading, error } = useTasks();
  const updateTaskMutation = useUpdateTask();
  const deleteTaskMutation = useDeleteTask();
  const reorderTasksMutation = useReorderTasks();

  const [search, setSearch] = useState("");
  const { activeView, selectedProjectId, setSelectedProjectId } = useViewStore();
  const openCommandPalette = useCommandPaletteStore((state) => state.open);

  const { data: projects = [] } = useProjects();
  const { data: labels = [] } = useLabels();
  const { data: presence } = useOnlineUsers();
  const setPresence = usePresenceStore((state) => state.setPresence);
  const deleteProjectMutation = useDeleteProject();

  // Modals & Drawers state
  const {
    isOpen: dailyPlannerOpen,
    open: openDailyPlanner,
    close: closeDailyPlanner,
  } = useDailyPlannerStore();
  const [taskModalOpen, setTaskModalOpen] = useState(false);
  const [projectModalOpen, setProjectModalOpen] = useState(false);
  const [projectToDelete, setProjectToDelete] = useState<Project | null>(null);
  const [activityDrawerOpen, setActivityDrawerOpen] = useState(false);
  const [apiKeysModalOpen, setApiKeysModalOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [coachOpen, setCoachOpen] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  // Drag & drop state
  const [activeId, setActiveId] = useState<string | null>(null);

  const setParsedTask = useAiStore((state) => state.setParsedTask);

  const tasks = useMemo(() => {
    return data?.content ?? [];
  }, [data]);

  const selectedProject = useMemo(() => {
    return projects.find((p) => p.id === selectedProjectId);
  }, [projects, selectedProjectId]);

  const selectedTask = useMemo(() => {
    return tasks.find((t) => t.id === selectedTaskId) ?? null;
  }, [tasks, selectedTaskId]);

  useEffect(() => {
    if (presence) {
      setPresence({
        online: presence.online,
        activeSessions: presence.activeSessions,
        username: presence.username,
      });
    }
  }, [presence, setPresence]);

  // Smart view + search filtering
  const filteredTasks = useMemo(() => {
    return tasks.filter((task) => {
      // 1. Text search
      const matchesSearch =
        !search.trim() ||
        task.title.toLowerCase().includes(search.toLowerCase()) ||
        (task.description &&
          task.description.toLowerCase().includes(search.toLowerCase()));

      if (!matchesSearch) return false;

      // 2. Project filter takes precedence if a project is selected
      if (selectedProjectId) {
        return task.projectId === selectedProjectId;
      }

      // 3. Smart view filter
      switch (activeView) {
        case "today":
          // Tasks due today or overdue
          return (
            isToday(task.dueDate) || isOverdue(task.dueDate, task.completed)
          );
        case "upcoming":
          return isUpcoming(task.dueDate);
        case "overdue":
          return isOverdue(task.dueDate, task.completed);
        case "inbox":
        default:
          return true;
      }
    });
  }, [tasks, search, selectedProjectId, activeView]);

  const handleDragStart = (event: DragStartEvent) => {
    setActiveId(event.active.id as string);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveId(null);
    const { active, over } = event;

    if (!over || active.id === over.id) {
      return;
    }

    const oldIndex = filteredTasks.findIndex((task) => task.id === active.id);
    const newIndex = filteredTasks.findIndex((task) => task.id === over.id);

    const reorderedTasks = arrayMove(filteredTasks, oldIndex, newIndex);
    reorderTasksMutation.mutate(reorderedTasks.map((task) => task.id));
  };

  // Quick keyboard shortcut 'n' to open create task modal
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      if (
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.isContentEditable
      ) {
        return;
      }

      if (event.key === "n" && !event.metaKey && !event.ctrlKey) {
        event.preventDefault();
        setTaskModalOpen(true);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  // Header Title and Icon metadata based on current view/project
  const viewHeaderInfo = useMemo(() => {
    if (selectedProject) {
      return {
        title: selectedProject.name,
        subtitle: `${filteredTasks.length} ${filteredTasks.length === 1 ? "task" : "tasks"} in project`,
        icon: (
          <div
            className="w-3.5 h-3.5 rounded-full shrink-0"
            style={{ backgroundColor: selectedProject.color }}
          />
        ),
      };
    }

    switch (activeView) {
      case "today":
        return {
          title: "Today",
          subtitle: `${filteredTasks.length} ${filteredTasks.length === 1 ? "task" : "tasks"} due today or needing attention`,
          icon: <CalendarDays size={24} className="text-emerald-500" />,
        };
      case "upcoming":
        return {
          title: "Upcoming",
          subtitle: `${filteredTasks.length} ${filteredTasks.length === 1 ? "task" : "tasks"} scheduled for future dates`,
          icon: <Calendar size={24} className="text-indigo-500" />,
        };
      case "overdue":
        return {
          title: "Overdue",
          subtitle: `${filteredTasks.length} ${filteredTasks.length === 1 ? "task" : "tasks"} past due date`,
          icon: <AlertCircle size={24} className="text-red-500" />,
        };
      case "inbox":
      default:
        return {
          title: "Inbox",
          subtitle: `${filteredTasks.length} ${filteredTasks.length === 1 ? "task" : "tasks"} in workspace`,
          icon: <Inbox size={24} className="text-blue-500" />,
        };
    }
  }, [selectedProject, activeView, filteredTasks.length]);

  if (isLoading) {
    return (
      <AppLayout>
        <div className="max-w-4xl mx-auto px-4 md:px-6 py-8 space-y-3">
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
        <div className="max-w-4xl mx-auto px-4 md:px-6 py-8 text-center text-red-500">
          Failed to load tasks. Please check your connection.
        </div>
      </AppLayout>
    );
  }

  const activeTask = filteredTasks.find((task) => task.id === activeId);

  return (
    <AppLayout
      onOpenCreateTask={() => setTaskModalOpen(true)}
      onOpenApiKeys={() => setApiKeysModalOpen(true)}
      onOpenActivity={() => setActivityDrawerOpen(true)}
    >
      <PageTransition>
        <OnlineUsers />

        <div className="max-w-4xl mx-auto px-4 md:px-6 py-6 md:py-8">
          {/* Top Bar Header */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
            <div>
              <div className="flex items-center gap-2.5">
                {viewHeaderInfo.icon}
                <h1 className="text-2xl sm:text-3xl font-bold tracking-tight">
                  {viewHeaderInfo.title}
                </h1>
                {selectedProject && (
                  <Button
                    variant="ghost"
                    onClick={() => setProjectToDelete(selectedProject)}
                    className="text-red-500 hover:text-red-600 hover:bg-red-500/10 cursor-pointer ml-1 p-1.5 h-auto rounded-lg"
                    title={`Delete project "${selectedProject.name}"`}
                  >
                    <Trash2 size={16} />
                  </Button>
                )}
              </div>
              <p className="text-xs text-muted mt-1">
                {viewHeaderInfo.subtitle}
              </p>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center gap-2 flex-wrap">
              <Button
                variant="ghost"
                onClick={() => setActivityDrawerOpen(true)}
                className="gap-1.5 text-xs sm:text-sm text-muted hover:text-foreground cursor-pointer"
                title="View Activity Feed"
              >
                <Activity size={15} />
                <span>Activity</span>
              </Button>

              <Button
                variant="ghost"
                onClick={() => setAiOpen(true)}
                className="gap-1.5 text-xs sm:text-sm text-accent hover:bg-accent/10 cursor-pointer"
                title="Open AI Natural Language Task Assistant"
              >
                <Sparkles size={15} />
                <span>AI Assistant</span>
              </Button>

              <Button
                variant="ghost"
                onClick={openDailyPlanner}
                className="gap-1.5 text-xs sm:text-sm text-purple-600 dark:text-purple-400 hover:bg-purple-500/10 cursor-pointer"
                title="AI Daily Planner & Eisenhower Matrix"
              >
                <CalendarCheck size={15} />
                <span>Daily Planner</span>
              </Button>

              <Button
                variant="ghost"
                onClick={() => setCoachOpen(true)}
                className="gap-1.5 text-xs sm:text-sm text-muted hover:text-foreground cursor-pointer"
                title="View AI Productivity Insights"
              >
                <Brain size={15} />
                <span className="hidden sm:inline">AI Insights</span>
              </Button>

              <Button
                variant="primary"
                onClick={() => setTaskModalOpen(true)}
                className="gap-1.5 text-xs sm:text-sm cursor-pointer shadow-sm"
              >
                <Plus size={16} />
                <span>Add Task</span>
              </Button>
            </div>
          </div>

          {/* Search Input Bar with Command Palette trigger badge */}
          <div className="relative mb-6">
            <Search
              size={16}
              className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted pointer-events-none"
            />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={`Search in ${viewHeaderInfo.title}...`}
              className="w-full pl-10 pr-24 py-2.5 bg-card border-border rounded-xl shadow-xs"
            />
            <button
              type="button"
              onClick={openCommandPalette}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 inline-flex items-center gap-1 px-2 py-1 rounded-md bg-secondary text-muted hover:text-foreground text-[11px] font-medium border border-border/80 transition-colors cursor-pointer"
              title="Open Command Palette (Ctrl+K)"
            >
              <Command size={11} />
              <span>K</span>
            </button>
          </div>

          {/* Task List Workspace */}
          {filteredTasks.length === 0 ? (
            <EmptyState
              title={
                search
                  ? "No matching tasks found"
                  : `No tasks in ${viewHeaderInfo.title}`
              }
              description={
                search
                  ? "Try searching for something else or clearing the search bar."
                  : activeView === "overdue"
                  ? "Great job! You have no overdue tasks."
                  : activeView === "today"
                  ? "No tasks due today. Click '+ Add Task' to plan your day."
                  : "Click '+ Add Task' or ask the '✨ AI Assistant' to create a task."
              }
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
                <div className="space-y-2">
                  {filteredTasks.map((task) => (
                    <SortableTaskCard
                      key={task.id}
                      task={task}
                      project={projects.find((p) => p.id === task.projectId)}
                      onSelect={() => setSelectedTaskId(task.id)}
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
                              toast.success(
                                !task.completed
                                  ? "Task completed"
                                  : "Task marked active"
                              );
                            },
                            onError: () => {
                              toast.error("Failed to update task");
                            },
                          }
                        );
                      }}
                      onEdit={() => setSelectedTaskId(task.id)}
                      onDelete={() => {
                        deleteTaskMutation.mutate(task.id, {
                          onSuccess: () => {
                            toast.success("Task deleted");
                            if (selectedTaskId === task.id) {
                              setSelectedTaskId(null);
                            }
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
                    project={projects.find((p) => p.id === activeTask.projectId)}
                    onToggle={() => {}}
                    onDelete={() => {}}
                    className="cursor-grabbing opacity-90 shadow-xl"
                  />
                ) : null}
              </DragOverlay>
            </DndContext>
          )}
        </div>

        {/* Drawers & Modals */}
        <CreateTaskModal
          open={taskModalOpen}
          onClose={() => setTaskModalOpen(false)}
          projects={projects}
          labels={labels}
        />

        <CreateProjectModal
          open={projectModalOpen}
          onClose={() => setProjectModalOpen(false)}
        />

        <TaskDetailsDrawer
          task={selectedTask}
          open={!!selectedTask}
          onClose={() => setSelectedTaskId(null)}
          projects={projects}
          labels={labels}
        />

        <AiAssistantDrawer
          open={aiOpen}
          onClose={() => setAiOpen(false)}
          onApply={(task) => {
            setParsedTask(task);
            setAiOpen(false);
            setTaskModalOpen(true);
          }}
        />

        <AiInsightsModal
          open={coachOpen}
          onClose={() => setCoachOpen(false)}
          tasks={tasks}
        />

        <AiDailyPlannerModal
          open={dailyPlannerOpen}
          onClose={closeDailyPlanner}
          tasks={tasks}
          projects={projects}
          onSelectTask={(taskId) => {
            closeDailyPlanner();
            setSelectedTaskId(taskId);
          }}
        />

        {/* Activity Drawer */}
        <ActivityDrawer
          open={activityDrawerOpen}
          onClose={() => setActivityDrawerOpen(false)}
        />

        {/* Delete Project Confirmation Modal */}
        <Modal
          open={!!projectToDelete}
          onClose={() => setProjectToDelete(null)}
          title="Delete Project"
        >
          <div className="space-y-4">
            <p className="text-sm text-foreground/80">
              Are you sure you want to delete project{" "}
              <strong className="text-foreground font-semibold">
                "{projectToDelete?.name}"
              </strong>
              ?
            </p>
            <div className="p-3 bg-secondary/50 rounded-xl text-xs text-muted border border-border">
              Tasks in this project will not be deleted; they will be safely unassigned and moved to your Inbox.
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="ghost"
                onClick={() => setProjectToDelete(null)}
                disabled={deleteProjectMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                disabled={deleteProjectMutation.isPending}
                onClick={() => {
                  if (!projectToDelete) return;
                  deleteProjectMutation.mutate(projectToDelete.id, {
                    onSuccess: () => {
                      toast.success(`Project "${projectToDelete.name}" deleted`);
                      if (selectedProjectId === projectToDelete.id) {
                        setSelectedProjectId(null);
                      }
                      setProjectToDelete(null);
                    },
                    onError: () => {
                      toast.error("Failed to delete project");
                    },
                  });
                }}
              >
                Delete Project
              </Button>
            </div>
          </div>
        </Modal>

        {/* Command Palette (Spotlight / Ctrl+K) */}
        <CommandPalette
          onOpenAddTask={() => setTaskModalOpen(true)}
          onOpenAiAssistant={() => setAiOpen(true)}
          onOpenAiInsights={() => setCoachOpen(true)}
          onOpenCreateProject={() => setProjectModalOpen(true)}
          onSelectTask={(taskId) => setSelectedTaskId(taskId)}
          onOpenApiKeys={() => setApiKeysModalOpen(true)}
          onOpenActivity={() => setActivityDrawerOpen(true)}
        />

        {/* API Keys & MCP Integration Modal */}
        {apiKeysModalOpen && (
          <ApiKeysModal
            open={apiKeysModalOpen}
            onClose={() => setApiKeysModalOpen(false)}
          />
        )}

        {/* Floating Action Button for quick task add (desktop only, mobile uses bottom nav + button) */}
        <button
          className="
            hidden md:flex
            fixed bottom-8 right-8
            w-13 h-13 rounded-full
            bg-primary text-primary-foreground
            shadow-lg
            items-center justify-center
            hover:scale-105 active:scale-95
            cursor-pointer
            transition-all duration-200 z-30
          "
          onClick={() => setTaskModalOpen(true)}
          title="Add task (or press 'n')"
        >
          <Plus size={22} />
        </button>
      </PageTransition>
    </AppLayout>
  );
};

export default DashboardPage;
