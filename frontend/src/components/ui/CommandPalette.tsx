import { useState, useEffect, useMemo, useRef } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  Search,
  Plus,
  Sparkles,
  Brain,
  FolderPlus,
  Inbox,
  CalendarDays,
  Calendar,
  AlertCircle,
  CheckCircle2,
  Circle,
  Moon,
  Sun,
  X,
  ArrowRight,
  CalendarCheck,
  KeyRound,
  Activity,
} from "lucide-react";
import { useCommandPaletteStore } from "../../store/commandPaletteStore";
import { useViewStore } from "../../store/viewStore";
import { useDailyPlannerStore } from "../../store/dailyPlannerStore";
import { useTasks } from "../../features/tasks/api/taskQueries";
import { useProjects } from "../../features/projects/projectQueries";
import { useThemeStore } from "../../store/themeStore";
import { cn } from "../../lib/cn";

type Props = {
  onOpenAddTask: () => void;
  onOpenAiAssistant: () => void;
  onOpenAiInsights: () => void;
  onOpenCreateProject: () => void;
  onSelectTask: (taskId: string) => void;
  onOpenApiKeys?: () => void;
  onOpenActivity?: () => void;
};

type CommandItem = {
  id: string;
  title: string;
  subtitle?: string;
  category: "Actions" | "Views" | "Projects" | "Tasks";
  icon: React.ReactNode;
  onSelect: () => void;
};

export const CommandPalette = ({
  onOpenAddTask,
  onOpenAiAssistant,
  onOpenAiInsights,
  onOpenCreateProject,
  onSelectTask,
  onOpenApiKeys,
  onOpenActivity,
}: Props) => {
  const { isOpen, close, toggle } = useCommandPaletteStore();
  const openDailyPlanner = useDailyPlannerStore((state) => state.open);
  const { setActiveView, setSelectedProjectId } = useViewStore();
  const { data: tasksData } = useTasks();
  const { data: projects = [] } = useProjects();
  const { theme, toggleTheme } = useThemeStore();

  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const tasks = useMemo(() => tasksData?.content ?? [], [tasksData]);

  // Global keyboard listener for Ctrl+K / Cmd+K
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        toggle();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [toggle]);

  // Focus input when opened
  useEffect(() => {
    if (isOpen) {
      setQuery("");
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  // Build commands list
  const allItems = useMemo<CommandItem[]>(() => {
    const items: CommandItem[] = [];

    // 1. Actions
    items.push({
      id: "action-add-task",
      title: "Add new task",
      subtitle: "Open task creation modal (shortcut: n)",
      category: "Actions",
      icon: <Plus size={16} className="text-accent" />,
      onSelect: () => {
        close();
        onOpenAddTask();
      },
    });

    items.push({
      id: "action-ai-assistant",
      title: "Open AI Task Assistant",
      subtitle: "Generate tasks using natural language",
      category: "Actions",
      icon: <Sparkles size={16} className="text-amber-500" />,
      onSelect: () => {
        close();
        onOpenAiAssistant();
      },
    });

    items.push({
      id: "action-ai-insights",
      title: "View AI Productivity Insights",
      subtitle: "Get coaching feedback on your tasks",
      category: "Actions",
      icon: <Brain size={16} className="text-purple-500" />,
      onSelect: () => {
        close();
        onOpenAiInsights();
      },
    });

    items.push({
      id: "action-daily-planner",
      title: "AI Daily Planner (Eisenhower Matrix)",
      subtitle: "Categorize tasks into 4 quadrants & time blocks",
      category: "Actions",
      icon: <CalendarCheck size={16} className="text-purple-500" />,
      onSelect: () => {
        close();
        openDailyPlanner();
      },
    });

    items.push({
      id: "action-create-project",
      title: "Create new project",
      subtitle: "Group your tasks into a project",
      category: "Actions",
      icon: <FolderPlus size={16} className="text-blue-500" />,
      onSelect: () => {
        close();
        onOpenCreateProject();
      },
    });

    if (onOpenApiKeys) {
      items.push({
        id: "action-api-keys",
        title: "API Keys & MCP Integration",
        subtitle: "Connect Claude Desktop, ChatGPT, Gemini, or Cursor via MCP",
        category: "Actions",
        icon: <KeyRound size={16} className="text-amber-500" />,
        onSelect: () => {
          close();
          onOpenApiKeys();
        },
      });
    }

    if (onOpenActivity) {
      items.push({
        id: "action-activity-log",
        title: "View Activity Log",
        subtitle: "Audit trail of changes made across your workspace",
        category: "Actions",
        icon: <Activity size={16} className="text-emerald-500" />,
        onSelect: () => {
          close();
          onOpenActivity();
        },
      });
    }

    items.push({
      id: "action-toggle-theme",
      title: `Toggle theme (current: ${theme})`,
      subtitle: "Switch between dark and light modes",
      category: "Actions",
      icon:
        theme === "light" ? (
          <Moon size={16} className="text-indigo-500" />
        ) : (
          <Sun size={16} className="text-amber-400" />
        ),
      onSelect: () => {
        toggleTheme();
        close();
      },
    });

    // 2. Views
    items.push({
      id: "view-inbox",
      title: "Go to Inbox",
      subtitle: "View all tasks or unorganized items",
      category: "Views",
      icon: <Inbox size={16} className="text-blue-500" />,
      onSelect: () => {
        setActiveView("inbox");
        close();
      },
    });

    items.push({
      id: "view-today",
      title: "Go to Today",
      subtitle: "Tasks due today or overdue",
      category: "Views",
      icon: <CalendarDays size={16} className="text-emerald-500" />,
      onSelect: () => {
        setActiveView("today");
        close();
      },
    });

    items.push({
      id: "view-upcoming",
      title: "Go to Upcoming",
      subtitle: "Tasks scheduled for future dates",
      category: "Views",
      icon: <Calendar size={16} className="text-indigo-500" />,
      onSelect: () => {
        setActiveView("upcoming");
        close();
      },
    });

    items.push({
      id: "view-overdue",
      title: "Go to Overdue",
      subtitle: "Incomplete tasks past their due date",
      category: "Views",
      icon: <AlertCircle size={16} className="text-red-500" />,
      onSelect: () => {
        setActiveView("overdue");
        close();
      },
    });

    // 3. Projects
    projects.forEach((project) => {
      items.push({
        id: `project-${project.id}`,
        title: `Project: ${project.name}`,
        subtitle: "Filter tasks by this project",
        category: "Projects",
        icon: (
          <div
            className="w-3 h-3 rounded-full shrink-0"
            style={{ backgroundColor: project.color }}
          />
        ),
        onSelect: () => {
          setSelectedProjectId(project.id);
          close();
        },
      });
    });

    // 4. Tasks (Matching search)
    tasks.forEach((task) => {
      items.push({
        id: `task-${task.id}`,
        title: task.title,
        subtitle: task.description || "Task in workspace",
        category: "Tasks",
        icon: task.completed ? (
          <CheckCircle2 size={16} className="text-emerald-500 shrink-0" />
        ) : (
          <Circle size={16} className="text-muted shrink-0" />
        ),
        onSelect: () => {
          close();
          onSelectTask(task.id);
        },
      });
    });

    return items;
  }, [
    projects,
    tasks,
    theme,
    close,
    onOpenAddTask,
    onOpenAiAssistant,
    onOpenAiInsights,
    onOpenCreateProject,
    onSelectTask,
    setActiveView,
    setSelectedProjectId,
    toggleTheme,
  ]);

  // Filter items based on query
  const filteredItems = useMemo(() => {
    if (!query.trim()) {
      // Default: show Actions, Views, and Projects
      return allItems.filter((item) => item.category !== "Tasks");
    }

    const q = query.toLowerCase();
    return allItems.filter(
      (item) =>
        item.title.toLowerCase().includes(q) ||
        (item.subtitle && item.subtitle.toLowerCase().includes(q))
    );
  }, [allItems, query]);

  // Clamp selection index
  useEffect(() => {
    setSelectedIndex(0);
  }, [query]);

  // Scroll active item into view
  useEffect(() => {
    if (itemRefs.current[selectedIndex]) {
      itemRefs.current[selectedIndex]?.scrollIntoView({
        block: "nearest",
      });
    }
  }, [selectedIndex]);

  // Handle keyboard navigation inside the palette
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      e.preventDefault();
      close();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelectedIndex((prev) =>
        prev < filteredItems.length - 1 ? prev + 1 : 0
      );
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelectedIndex((prev) =>
        prev > 0 ? prev - 1 : filteredItems.length - 1
      );
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (filteredItems[selectedIndex]) {
        filteredItems[selectedIndex].onSelect();
      }
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-start justify-center pt-16 sm:pt-24 px-4">
          {/* Backdrop */}
          <motion.div
            onClick={close}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="fixed inset-0 bg-black/60 backdrop-blur-xs"
          />

          {/* Palette Dialog */}
          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: -10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: -10 }}
            transition={{ duration: 0.2, ease: "easeOut" }}
            className="relative w-full max-w-xl bg-card border border-border rounded-2xl shadow-2xl overflow-hidden z-10 flex flex-col max-h-[75vh]"
          >
            {/* Search Input Box */}
            <div className="flex items-center px-4 py-3.5 border-b border-border gap-3">
              <Search size={18} className="text-muted shrink-0" />
              <input
                ref={inputRef}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Type a command or search tasks..."
                className="w-full bg-transparent border-none text-foreground placeholder:text-muted focus:outline-none text-sm sm:text-base font-medium"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => setQuery("")}
                  className="text-muted hover:text-foreground cursor-pointer p-1"
                >
                  <X size={16} />
                </button>
              )}
              <kbd className="hidden sm:inline-flex items-center gap-0.5 px-2 py-0.5 text-[11px] font-mono text-muted bg-secondary rounded border border-border">
                ESC
              </kbd>
            </div>

            {/* Results List */}
            <div className="overflow-y-auto p-2 space-y-1 divide-y divide-border/30">
              {filteredItems.length === 0 ? (
                <div className="py-10 text-center text-sm text-muted">
                  No commands or tasks found matching &ldquo;{query}&rdquo;
                </div>
              ) : (
                filteredItems.map((item, index) => {
                  const isSelected = selectedIndex === index;
                  return (
                    <button
                      key={item.id}
                      ref={(el) => {
                        itemRefs.current[index] = el;
                      }}
                      onClick={item.onSelect}
                      onMouseEnter={() => setSelectedIndex(index)}
                      className={cn(
                        "w-full flex items-center justify-between gap-3 px-3 py-2.5 rounded-xl text-left transition-colors cursor-pointer",
                        isSelected
                          ? "bg-secondary text-foreground"
                          : "text-muted hover:text-foreground hover:bg-secondary/60"
                      )}
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="shrink-0 p-1.5 rounded-lg bg-card border border-border">
                          {item.icon}
                        </div>
                        <div className="min-w-0">
                          <p
                            className={cn(
                              "text-sm font-medium truncate",
                              isSelected ? "text-foreground" : "text-foreground/90"
                            )}
                          >
                            {item.title}
                          </p>
                          {item.subtitle && (
                            <p className="text-xs text-muted truncate">
                              {item.subtitle}
                            </p>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-[10px] uppercase tracking-wider font-semibold px-2 py-0.5 rounded-md bg-secondary text-muted border border-border/50">
                          {item.category}
                        </span>
                        {isSelected && (
                          <ArrowRight size={14} className="text-muted" />
                        )}
                      </div>
                    </button>
                  );
                })
              )}
            </div>

            {/* Footer hints */}
            <div className="px-4 py-2 bg-secondary/50 border-t border-border flex items-center justify-between text-[11px] text-muted">
              <div className="flex items-center gap-3">
                <span className="flex items-center gap-1">
                  <kbd className="font-mono bg-card px-1 py-0.5 rounded border border-border">
                    ↑
                  </kbd>
                  <kbd className="font-mono bg-card px-1 py-0.5 rounded border border-border">
                    ↓
                  </kbd>{" "}
                  navigate
                </span>
                <span className="flex items-center gap-1">
                  <kbd className="font-mono bg-card px-1.5 py-0.5 rounded border border-border">
                    ↵
                  </kbd>{" "}
                  select
                </span>
              </div>
              <span>
                <kbd className="font-mono bg-card px-1.5 py-0.5 rounded border border-border">
                  Ctrl
                </kbd>{" "}
                +{" "}
                <kbd className="font-mono bg-card px-1.5 py-0.5 rounded border border-border">
                  K
                </kbd>
              </span>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};

export default CommandPalette;
