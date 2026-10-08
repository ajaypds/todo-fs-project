import { useState, useMemo } from "react";
import { Modal } from "../../../components/ui/Modal";
import type { Task } from "../../tasks/types/taskTypes";
import type { Project } from "../../projects/projectTypes";
import type {
  DailyPlannerTaskSnapshot,
  MatrixTaskItem,
} from "../types/aiTypes";
import { useDailyPlanner } from "../api/aiQueries";
import { useUpdateTask } from "../../tasks/api/taskQueries";
import { Button } from "../../../components/ui/Button";
import { PriorityBadge } from "../../../components/ui/PriorityBadge";
import { formatDate } from "../../../utils/date";
import {
  Sparkles,
  Clock,
  CheckCircle2,
  Circle,
  Target,
  Loader2,
  RefreshCw,
  Flame,
  Zap,
  Coffee,
  Compass,
  ChevronRight,
} from "lucide-react";
import toast from "react-hot-toast";
import { cn } from "../../../lib/cn";

type Props = {
  open: boolean;
  onClose: () => void;
  tasks: Task[];
  projects: Project[];
  onSelectTask?: (taskId: string) => void;
};

export const AiDailyPlannerModal = ({
  open,
  onClose,
  tasks,
  projects,
  onSelectTask,
}: Props) => {
  const dailyPlannerMutation = useDailyPlanner();
  const updateTaskMutation = useUpdateTask();
  const [activeTab, setActiveTab] = useState<"matrix" | "schedule">("matrix");

  // Project lookup map
  const projectMap = useMemo(() => {
    const map = new Map<string, Project>();
    projects.forEach((p) => map.set(p.id, p));
    return map;
  }, [projects]);

  // Active uncompleted tasks map
  const taskMap = useMemo(() => {
    const map = new Map<string, Task>();
    tasks.forEach((t) => map.set(t.id, t));
    return map;
  }, [tasks]);

  const activeTasks = useMemo(() => {
    return tasks.filter((t) => !t.completed);
  }, [tasks]);

  const handleGeneratePlan = () => {
    const taskSnapshots: DailyPlannerTaskSnapshot[] = activeTasks.map((t) => {
      const project = t.projectId ? projectMap.get(t.projectId) : undefined;
      const subtaskCount = t.subtasks?.length ?? 0;
      const completedSubtaskCount =
        t.subtasks?.filter((st) => st.completed).length ?? 0;

      return {
        id: t.id,
        title: t.title,
        description: t.description,
        priority: t.priority ?? 1,
        dueDate: t.dueDate,
        projectName: project?.name,
        subtaskCount,
        completedSubtaskCount,
      };
    });

    const userTimezone =
      Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Kolkata";

    dailyPlannerMutation.mutate(
      {
        tasks: taskSnapshots,
        userTimezone,
      },
      {
        onError: () => {
          toast.error("Failed to generate AI Daily Plan. Please try again.");
        },
        onSuccess: () => {
          toast.success("AI Daily Plan generated!");
        },
      }
    );
  };

  const handleToggleTask = (taskId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const task = taskMap.get(taskId);
    if (!task) return;

    const nextState = !task.completed;
    updateTaskMutation.mutate(
      {
        taskId: task.id,
        payload: { completed: nextState },
      },
      {
        onSuccess: () => {
          toast.success(
            nextState ? "Task completed" : "Task marked active"
          );
        },
      }
    );
  };

  const plan = dailyPlannerMutation.data;

  // Quadrant item renderer
  const renderQuadrantCard = (
    item: MatrixTaskItem,
    accentColor: "rose" | "blue" | "amber" | "slate"
  ) => {
    const realTask = taskMap.get(item.taskId);
    const isCompleted = realTask?.completed ?? false;
    const project = realTask?.projectId
      ? projectMap.get(realTask.projectId)
      : undefined;

    return (
      <div
        key={item.taskId}
        onClick={() => onSelectTask?.(item.taskId)}
        className={cn(
          "group p-3 rounded-xl border bg-card/90 hover:bg-card transition-all cursor-pointer select-none space-y-1.5 shadow-xs",
          isCompleted && "opacity-60 bg-secondary/40",
          accentColor === "rose" && "border-rose-500/20 hover:border-rose-500/50",
          accentColor === "blue" && "border-blue-500/20 hover:border-blue-500/50",
          accentColor === "amber" &&
            "border-amber-500/20 hover:border-amber-500/50",
          accentColor === "slate" && "border-border hover:border-border/90"
        )}
      >
        <div className="flex items-start gap-2.5">
          <button
            type="button"
            onClick={(e) => handleToggleTask(item.taskId, e)}
            className="mt-0.5 shrink-0 text-muted hover:text-foreground cursor-pointer focus:outline-none"
            aria-label={isCompleted ? "Mark active" : "Mark complete"}
          >
            {isCompleted ? (
              <CheckCircle2 size={16} className="text-emerald-500" />
            ) : (
              <Circle size={16} className="text-muted group-hover:text-foreground" />
            )}
          </button>

          <div className="flex-1 min-w-0">
            <div className="flex items-center justify-between gap-2">
              <span
                className={cn(
                  "text-xs sm:text-sm font-semibold truncate",
                  isCompleted && "line-through text-muted"
                )}
              >
                {item.title}
              </span>
              {realTask && <PriorityBadge priority={realTask.priority} />}
            </div>

            {item.rationale && (
              <p className="text-[11px] text-muted line-clamp-2 mt-0.5">
                💡 {item.rationale}
              </p>
            )}

            <div className="flex items-center gap-2 pt-1 flex-wrap text-[10px] text-muted">
              {project && (
                <span className="inline-flex items-center gap-1 px-1.5 py-0.2 rounded bg-secondary">
                  <span
                    className="w-1.5 h-1.5 rounded-full"
                    style={{ backgroundColor: project.color }}
                  />
                  <span className="truncate max-w-20">{project.name}</span>
                </span>
              )}

              {realTask?.dueDate && (
                <span className="inline-flex items-center gap-0.5">
                  <Clock size={10} />
                  <span>{formatDate(realTask.dueDate)}</span>
                </span>
              )}

              {item.urgencyScore !== undefined && (
                <span className="opacity-75">
                  Urgency: {item.urgencyScore}/10
                </span>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title=""
      maxWidth="max-w-5xl"
      className="p-0 overflow-hidden"
    >
      <div className="flex flex-col h-[85vh]">
        {/* Top Header */}
        <div className="p-5 sm:p-6 border-b border-border bg-card/50 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 shrink-0">
          <div>
            <div className="flex items-center gap-2">
              <div className="p-1.5 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400">
                <Sparkles size={18} />
              </div>
              <h2 className="text-lg sm:text-xl font-bold tracking-tight">
                AI Smart Daily Planner
              </h2>
            </div>
            <p className="text-xs text-muted mt-1">
              Eisenhower Matrix (Urgent vs. Important) & Optimized Daily Schedule
            </p>
          </div>

          <div className="flex items-center gap-2 self-stretch sm:self-auto justify-between sm:justify-end">
            {plan && (
              <div className="flex items-center p-0.5 rounded-xl bg-secondary/80 border border-border/60 text-xs">
                <button
                  type="button"
                  onClick={() => setActiveTab("matrix")}
                  className={cn(
                    "px-3 py-1.5 rounded-lg font-medium transition-all cursor-pointer flex items-center gap-1.5",
                    activeTab === "matrix"
                      ? "bg-card text-foreground shadow-xs font-semibold"
                      : "text-muted hover:text-foreground"
                  )}
                >
                  <Target size={13} />
                  <span>Eisenhower Matrix</span>
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab("schedule")}
                  className={cn(
                    "px-3 py-1.5 rounded-lg font-medium transition-all cursor-pointer flex items-center gap-1.5",
                    activeTab === "schedule"
                      ? "bg-card text-foreground shadow-xs font-semibold"
                      : "text-muted hover:text-foreground"
                  )}
                >
                  <Clock size={13} />
                  <span>Time Blocks</span>
                </button>
              </div>
            )}

            <Button
              type="button"
              variant="primary"
              onClick={handleGeneratePlan}
              disabled={dailyPlannerMutation.isPending || activeTasks.length === 0}
              className="text-xs px-3.5 py-1.5 gap-1.5 shrink-0 bg-purple-600 hover:bg-purple-700 text-white"
            >
              {dailyPlannerMutation.isPending ? (
                <>
                  <Loader2 size={13} className="animate-spin" />
                  <span>Analyzing...</span>
                </>
              ) : plan ? (
                <>
                  <RefreshCw size={13} />
                  <span>Regenerate</span>
                </>
              ) : (
                <>
                  <Zap size={13} />
                  <span>Generate Plan</span>
                </>
              )}
            </Button>
          </div>
        </div>

        {/* Scrollable Content Area */}
        <div className="flex-1 overflow-y-auto p-5 sm:p-6 space-y-6">
          {/* Empty / Intro State before generating */}
          {!plan && !dailyPlannerMutation.isPending && (
            <div className="py-12 px-4 max-w-xl mx-auto text-center space-y-5">
              <div className="w-14 h-14 mx-auto rounded-2xl bg-purple-500/10 text-purple-600 dark:text-purple-400 flex items-center justify-center shadow-inner">
                <Compass size={28} />
              </div>
              <div className="space-y-2">
                <h3 className="text-lg font-semibold text-foreground">
                  Transform Tasks into an Actionable Daily Strategy
                </h3>
                <p className="text-xs sm:text-sm text-muted leading-relaxed">
                  The AI Daily Planner evaluates your {activeTasks.length} active
                  tasks using President Eisenhower’s decision matrix to separate
                  distractions from high-impact work, then schedules them into
                  ideal cognitive focus windows.
                </p>
              </div>

              {/* 4 Quadrants Preview Pills */}
              <div className="grid grid-cols-2 gap-2 text-left pt-2">
                <div className="p-3 rounded-xl bg-rose-500/5 border border-rose-500/20">
                  <div className="text-xs font-semibold text-rose-600 dark:text-rose-400 flex items-center gap-1">
                    <Flame size={12} />
                    <span>Q1: Do First</span>
                  </div>
                  <p className="text-[11px] text-muted mt-0.5">
                    Urgent & Important (Today’s fire drills)
                  </p>
                </div>

                <div className="p-3 rounded-xl bg-blue-500/5 border border-blue-500/20">
                  <div className="text-xs font-semibold text-blue-600 dark:text-blue-400 flex items-center gap-1">
                    <Target size={12} />
                    <span>Q2: Schedule</span>
                  </div>
                  <p className="text-[11px] text-muted mt-0.5">
                    Not Urgent, Important (Strategic deep work)
                  </p>
                </div>

                <div className="p-3 rounded-xl bg-amber-500/5 border border-amber-500/20">
                  <div className="text-xs font-semibold text-amber-600 dark:text-amber-400 flex items-center gap-1">
                    <Zap size={12} />
                    <span>Q3: Delegate / Batch</span>
                  </div>
                  <p className="text-[11px] text-muted mt-0.5">
                    Urgent, Not Important (Quick admin hits)
                  </p>
                </div>

                <div className="p-3 rounded-xl bg-slate-500/5 border border-border">
                  <div className="text-xs font-semibold text-muted flex items-center gap-1">
                    <Coffee size={12} />
                    <span>Q4: Eliminate</span>
                  </div>
                  <p className="text-[11px] text-muted mt-0.5">
                    Neither (Backlog / Low priority)
                  </p>
                </div>
              </div>

              <div className="pt-2">
                <Button
                  type="button"
                  onClick={handleGeneratePlan}
                  disabled={activeTasks.length === 0}
                  className="px-6 py-2.5 bg-purple-600 hover:bg-purple-700 text-white font-medium text-xs sm:text-sm gap-2"
                >
                  <Sparkles size={15} />
                  <span>
                    Generate Daily Plan ({activeTasks.length} Active Tasks)
                  </span>
                </Button>
                {activeTasks.length === 0 && (
                  <p className="text-xs text-muted mt-2 italic">
                    Add some active tasks first to build your daily plan.
                  </p>
                )}
              </div>
            </div>
          )}

          {/* Loading Skeleton */}
          {dailyPlannerMutation.isPending && (
            <div className="py-16 text-center space-y-4">
              <Loader2 size={36} className="animate-spin text-purple-500 mx-auto" />
              <div className="space-y-1">
                <p className="text-sm font-semibold">
                  Synthesizing your daily plan...
                </p>
                <p className="text-xs text-muted max-w-sm mx-auto">
                  Analyzing urgency, deadlines, priorities, and building your
                  Eisenhower quadrants and focus time blocks.
                </p>
              </div>
            </div>
          )}

          {/* Plan Content */}
          {plan && !dailyPlannerMutation.isPending && (
            <>
              {/* Executive Summary & Tactical Coaching Banner */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div className="md:col-span-2 p-4 rounded-xl bg-purple-500/5 border border-purple-200 dark:border-purple-800/50 space-y-1.5">
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-purple-700 dark:text-purple-300">
                    <Sparkles size={14} className="text-purple-500" />
                    <span>Daily Executive Briefing</span>
                  </div>
                  <p className="text-xs sm:text-sm text-foreground leading-relaxed">
                    {plan.summary}
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-amber-500/5 border border-amber-200 dark:border-amber-800/50 space-y-1.5">
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-amber-700 dark:text-amber-300">
                    <Coffee size={14} className="text-amber-500" />
                    <span>Coach's Tactical Tip</span>
                  </div>
                  <p className="text-xs text-foreground/90 leading-relaxed">
                    {plan.coachingTip}
                  </p>
                </div>
              </div>

              {/* View 1: Eisenhower Matrix 2x2 Grid */}
              {activeTab === "matrix" && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between text-xs text-muted px-1">
                    <span className="font-semibold uppercase tracking-wider text-[11px]">
                      Eisenhower Matrix Quadrants
                    </span>
                    <span>Click any task to inspect details</span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* Q1: Do First */}
                    <div className="p-4 rounded-2xl bg-rose-500/5 border border-rose-500/30 flex flex-col space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                          <h4 className="text-xs font-bold uppercase tracking-wider text-rose-600 dark:text-rose-400">
                            Q1: Do First
                          </h4>
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-rose-500/10 text-rose-600 dark:text-rose-400 font-semibold">
                            Urgent & Important
                          </span>
                        </div>
                        <span className="text-xs text-muted font-medium">
                          {plan.matrix.doFirst?.length ?? 0}
                        </span>
                      </div>

                      <div className="space-y-2 flex-1">
                        {!plan.matrix.doFirst ||
                        plan.matrix.doFirst.length === 0 ? (
                          <p className="text-xs text-muted italic p-3 text-center">
                            No immediate crises or urgent fires!
                          </p>
                        ) : (
                          plan.matrix.doFirst.map((item) =>
                            renderQuadrantCard(item, "rose")
                          )
                        )}
                      </div>
                    </div>

                    {/* Q2: Schedule / Deep Work */}
                    <div className="p-4 rounded-2xl bg-blue-500/5 border border-blue-500/30 flex flex-col space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-2.5 h-2.5 rounded-full bg-blue-500" />
                          <h4 className="text-xs font-bold uppercase tracking-wider text-blue-600 dark:text-blue-400">
                            Q2: Schedule
                          </h4>
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-blue-500/10 text-blue-600 dark:text-blue-400 font-semibold">
                            Important, Not Urgent
                          </span>
                        </div>
                        <span className="text-xs text-muted font-medium">
                          {plan.matrix.schedule?.length ?? 0}
                        </span>
                      </div>

                      <div className="space-y-2 flex-1">
                        {!plan.matrix.schedule ||
                        plan.matrix.schedule.length === 0 ? (
                          <p className="text-xs text-muted italic p-3 text-center">
                            No scheduled deep work items.
                          </p>
                        ) : (
                          plan.matrix.schedule.map((item) =>
                            renderQuadrantCard(item, "blue")
                          )
                        )}
                      </div>
                    </div>

                    {/* Q3: Delegate / Quick Batch */}
                    <div className="p-4 rounded-2xl bg-amber-500/5 border border-amber-500/30 flex flex-col space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
                          <h4 className="text-xs font-bold uppercase tracking-wider text-amber-600 dark:text-amber-400">
                            Q3: Delegate / Batch
                          </h4>
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-amber-500/10 text-amber-600 dark:text-amber-400 font-semibold">
                            Urgent, Not Important
                          </span>
                        </div>
                        <span className="text-xs text-muted font-medium">
                          {plan.matrix.delegate?.length ?? 0}
                        </span>
                      </div>

                      <div className="space-y-2 flex-1">
                        {!plan.matrix.delegate ||
                        plan.matrix.delegate.length === 0 ? (
                          <p className="text-xs text-muted italic p-3 text-center">
                            No small urgent administrative chores.
                          </p>
                        ) : (
                          plan.matrix.delegate.map((item) =>
                            renderQuadrantCard(item, "amber")
                          )
                        )}
                      </div>
                    </div>

                    {/* Q4: Eliminate / Backlog */}
                    <div className="p-4 rounded-2xl bg-secondary/30 border border-border flex flex-col space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-2.5 h-2.5 rounded-full bg-slate-400" />
                          <h4 className="text-xs font-bold uppercase tracking-wider text-muted">
                            Q4: Eliminate / Backlog
                          </h4>
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-secondary text-muted font-semibold">
                            Neither
                          </span>
                        </div>
                        <span className="text-xs text-muted font-medium">
                          {plan.matrix.eliminate?.length ?? 0}
                        </span>
                      </div>

                      <div className="space-y-2 flex-1">
                        {!plan.matrix.eliminate ||
                        plan.matrix.eliminate.length === 0 ? (
                          <p className="text-xs text-muted italic p-3 text-center">
                            No backlog or low-priority items.
                          </p>
                        ) : (
                          plan.matrix.eliminate.map((item) =>
                            renderQuadrantCard(item, "slate")
                          )
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* View 2: Daily Schedule / Time Blocks */}
              {activeTab === "schedule" && (
                <div className="space-y-4">
                  <div className="text-xs text-muted px-1 font-semibold uppercase tracking-wider text-[11px]">
                    Optimized Energy & Cognitive Time Blocks
                  </div>

                  <div className="space-y-3">
                    {plan.timeBlocks.map((block, idx) => (
                      <div
                        key={idx}
                        className="p-4 rounded-2xl bg-card border border-border space-y-3 shadow-xs"
                      >
                        <div className="flex items-center justify-between flex-wrap gap-2">
                          <div className="flex items-center gap-2">
                            <span className="p-1.5 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
                              <Clock size={15} />
                            </span>
                            <div>
                              <h4 className="text-sm font-bold text-foreground">
                                {block.timePeriod}
                              </h4>
                              <p className="text-xs text-muted">
                                {block.focusTheme}
                              </p>
                            </div>
                          </div>
                          <span className="text-xs px-2.5 py-1 rounded-full bg-secondary text-muted font-medium">
                            {block.taskTitles?.length ?? 0} tasks
                          </span>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
                          {block.taskTitles?.map((title, tIdx) => {
                            const taskId = block.taskIds?.[tIdx];
                            const realTask = taskId
                              ? taskMap.get(taskId)
                              : undefined;

                            return (
                              <div
                                key={tIdx}
                                onClick={() => taskId && onSelectTask?.(taskId)}
                                className="flex items-center justify-between gap-2 p-2.5 rounded-xl bg-secondary/50 hover:bg-secondary border border-border/60 transition-colors cursor-pointer text-xs"
                              >
                                <div className="flex items-center gap-2 truncate">
                                  <ChevronRight
                                    size={13}
                                    className="text-muted shrink-0"
                                  />
                                  <span
                                    className={cn(
                                      "font-medium truncate",
                                      realTask?.completed &&
                                        "line-through text-muted"
                                    )}
                                  >
                                    {title}
                                  </span>
                                </div>
                                {realTask && (
                                  <PriorityBadge priority={realTask.priority} />
                                )}
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-border bg-card/50 flex items-center justify-between text-xs text-muted shrink-0">
          <span>
            {activeTasks.length} active tasks • {plan ? "Plan ready" : "Awaiting analysis"}
          </span>
          <Button
            type="button"
            variant="ghost"
            onClick={onClose}
            className="text-xs h-8 px-4"
          >
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
};

export default AiDailyPlannerModal;
