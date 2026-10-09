import { useEffect, useMemo, type ReactNode } from "react";
import { ProjectSidebar } from "../../features/projects/components/ProjectSidebar";
import { ThemeToggle } from "../ui/ThemeToggle";
import { RealtimeStatusIndicator } from "../../features/realtime/components/RealtimeStatusIndicator";
import {
  LogOut,
  Menu,
  X,
  Inbox,
  CalendarDays,
  Calendar,
  AlertCircle,
  Command,
  CalendarCheck,
  Globe,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  KeyRound,
  Activity,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { useSidebarStore } from "../../store/sidebarStore";
import { useViewStore } from "../../store/viewStore";
import { useCommandPaletteStore } from "../../store/commandPaletteStore";
import { useDailyPlannerStore } from "../../store/dailyPlannerStore";
import { useTasks } from "../../features/tasks/api/taskQueries";
import { isToday, isOverdue, isUpcoming, getUserTimezone } from "../../utils/date";
import { useCurrentUser, useUpdateTimezone } from "../../features/users/api/userQueries";
import { Button } from "../ui/Button";
import { cn } from "../../lib/cn";

type Props = {
  children: ReactNode;
  onOpenCreateTask?: () => void;
  onOpenApiKeys?: () => void;
  onOpenActivity?: () => void;
};

export const AppLayout = ({ children, onOpenCreateTask, onOpenApiKeys, onOpenActivity }: Props) => {
  const navigate = useNavigate();
  const token = useAuthStore((state) => state.token);
  const logout = useAuthStore((state) => state.logout);
  const { open, toggle, setOpen, desktopCollapsed, toggleDesktopCollapsed } =
    useSidebarStore();
  const { activeView, setActiveView, selectedProjectId } = useViewStore();
  const openCommandPalette = useCommandPaletteStore((state) => state.open);
  const openDailyPlanner = useDailyPlannerStore((state) => state.open);

  const { data: userProfile } = useCurrentUser(!!token);
  const updateTimezoneMutation = useUpdateTimezone();
  const localTimezone = useMemo(() => getUserTimezone(), []);
  const activeTimezone = userProfile?.timezone || localTimezone;

  useEffect(() => {
    if (
      userProfile &&
      userProfile.timezone === "UTC" &&
      localTimezone !== "UTC" &&
      !updateTimezoneMutation.isPending
    ) {
      updateTimezoneMutation.mutate({ timezone: localTimezone });
    }
  }, [userProfile, localTimezone, updateTimezoneMutation]);

  const { data: tasksData } = useTasks();
  const tasks = useMemo(() => tasksData?.content ?? [], [tasksData]);

  // Dynamic live count calculations
  const counts = useMemo(() => {
    let inbox = 0;
    let today = 0;
    let upcoming = 0;
    let overdue = 0;

    tasks.forEach((t) => {
      if (t.completed) return;

      inbox++;

      if (isToday(t.dueDate) || isOverdue(t.dueDate, t.completed)) {
        today++;
      }
      if (isUpcoming(t.dueDate)) {
        upcoming++;
      }
      if (isOverdue(t.dueDate, t.completed)) {
        overdue++;
      }
    });

    return { inbox, today, upcoming, overdue };
  }, [tasks]);

  const handleSelectView = (view: "inbox" | "today" | "upcoming" | "overdue") => {
    setActiveView(view);
    setOpen(false); // Close mobile sidebar if open
  };

  return (
    <div className="flex h-screen bg-background text-foreground">
      {/* Mobile Top Header */}
      <div
        className="
          md:hidden
          fixed top-0 left-0 right-0
          h-16
          bg-card/90
          backdrop-blur-xl
          border-b border-border
          z-40
          flex items-center
          justify-between
          px-4
        "
      >
        <div className="flex items-center gap-2">
          <h1 className="font-bold text-lg">TodoFlow</h1>
        </div>

        <div className="flex items-center gap-2">
          <RealtimeStatusIndicator compact />
          <button
            type="button"
            onClick={openCommandPalette}
            className="p-2 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer"
            title="Search & Commands (Ctrl+K)"
          >
            <Command size={18} />
          </button>
          {onOpenApiKeys && (
            <button
              type="button"
              onClick={onOpenApiKeys}
              className="p-2 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer"
              title="API Keys & MCP Integration"
            >
              <KeyRound size={18} />
            </button>
          )}
          {onOpenActivity && (
            <button
              type="button"
              onClick={onOpenActivity}
              className="p-2 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer"
              title="Activity Log"
            >
              <Activity size={18} />
            </button>
          )}
          <ThemeToggle />
          <button onClick={toggle} className="p-1 cursor-pointer">
            {open ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
      </div>

      {/* Sidebar Navigation */}
      <aside
        className={cn(
          `
            fixed md:static
            inset-y-0 left-0
            z-50
            border-r border-border
            bg-card/90
            backdrop-blur-xl
            transition-all
            duration-300
            flex flex-col
          `,
          // Mobile state
          open ? "translate-x-0 w-72 px-4 py-6" : "-translate-x-full md:translate-x-0",
          // Desktop state
          desktopCollapsed
            ? "md:w-18 md:px-2 md:py-5"
            : "md:w-72 md:px-4 md:py-6"
        )}
      >
        {/* Sidebar Header */}
        <div className="flex items-center justify-between mb-6 px-1">
          {desktopCollapsed ? (
            <div className="hidden md:flex flex-col items-center w-full gap-3">
              <span className="font-bold text-base text-accent">TF</span>
              <button
                type="button"
                onClick={toggleDesktopCollapsed}
                className="p-1.5 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                title="Expand sidebar"
              >
                <PanelLeftOpen size={18} />
              </button>
              {onOpenApiKeys && (
                <button
                  type="button"
                  onClick={onOpenApiKeys}
                  className="p-1.5 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                  title="API Keys & MCP Integration"
                >
                  <KeyRound size={17} />
                </button>
              )}
              {onOpenActivity && (
                <button
                  type="button"
                  onClick={onOpenActivity}
                  className="p-1.5 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                  title="Activity Log"
                >
                  <Activity size={17} />
                </button>
              )}
            </div>
          ) : (
            <>
              <h1 className="text-2xl font-bold tracking-tight">TodoFlow</h1>
              <div className="hidden md:flex items-center gap-1">
                <RealtimeStatusIndicator />
                {onOpenApiKeys && (
                  <button
                    type="button"
                    onClick={onOpenApiKeys}
                    className="p-1.5 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                    title="API Keys & MCP Integration"
                  >
                    <KeyRound size={17} />
                  </button>
                )}
                {onOpenActivity && (
                  <button
                    type="button"
                    onClick={onOpenActivity}
                    className="p-1.5 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                    title="Activity Log"
                  >
                    <Activity size={17} />
                  </button>
                )}
                <ThemeToggle />
                <button
                  type="button"
                  onClick={toggleDesktopCollapsed}
                  className="p-1.5 rounded-lg text-muted hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                  title="Collapse sidebar"
                >
                  <PanelLeftClose size={18} />
                </button>
              </div>
            </>
          )}

          {/* Mobile close button in sidebar */}
          <div className="md:hidden flex items-center gap-2">
            <RealtimeStatusIndicator compact />
            <ThemeToggle />
            <button onClick={() => setOpen(false)} className="p-1 text-muted hover:text-foreground">
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Command Palette Quick Trigger Button */}
        {desktopCollapsed ? (
          <button
            type="button"
            onClick={() => {
              openCommandPalette();
              setOpen(false);
            }}
            className="hidden md:flex w-10 h-10 mx-auto items-center justify-center mb-4 rounded-xl bg-secondary/60 hover:bg-secondary text-muted hover:text-foreground border border-border/60 transition-all cursor-pointer"
            title="Search & Commands (Ctrl+K)"
          >
            <Command size={16} />
          </button>
        ) : (
          <button
            type="button"
            onClick={() => {
              openCommandPalette();
              setOpen(false);
            }}
            className="w-full flex items-center justify-between gap-2 px-3 py-2 mb-4 rounded-xl bg-secondary/60 hover:bg-secondary text-muted hover:text-foreground text-xs font-medium border border-border/60 transition-all cursor-pointer"
          >
            <span className="flex items-center gap-2">
              <Command size={14} />
              <span>Search & Commands</span>
            </span>
            <kbd className="font-mono text-[10px] bg-card px-1.5 py-0.5 rounded border border-border">
              Ctrl K
            </kbd>
          </button>
        )}

        {/* Smart Views Navigation */}
        {desktopCollapsed ? (
          <nav className="hidden md:flex flex-col space-y-1.5">
            {/* Inbox */}
            <button
              type="button"
              onClick={() => handleSelectView("inbox")}
              className={cn(
                "w-10 h-10 mx-auto rounded-xl flex items-center justify-center transition-all cursor-pointer relative",
                activeView === "inbox" && !selectedProjectId
                  ? "bg-secondary text-foreground font-semibold shadow-xs"
                  : "text-muted hover:text-foreground hover:bg-secondary/70"
              )}
              title={`Inbox (${counts.inbox})`}
            >
              <Inbox size={18} className="text-blue-500" />
              {counts.inbox > 0 && (
                <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-blue-500" />
              )}
            </button>

            {/* Today */}
            <button
              type="button"
              onClick={() => handleSelectView("today")}
              className={cn(
                "w-10 h-10 mx-auto rounded-xl flex items-center justify-center transition-all cursor-pointer relative",
                activeView === "today" && !selectedProjectId
                  ? "bg-secondary text-foreground font-semibold shadow-xs"
                  : "text-muted hover:text-foreground hover:bg-secondary/70"
              )}
              title={`Today (${counts.today})`}
            >
              <CalendarDays size={18} className="text-emerald-500" />
              {counts.today > 0 && (
                <span
                  className={cn(
                    "absolute top-1 right-1 w-2 h-2 rounded-full",
                    counts.overdue > 0 ? "bg-amber-500" : "bg-emerald-500"
                  )}
                />
              )}
            </button>

            {/* Upcoming */}
            <button
              type="button"
              onClick={() => handleSelectView("upcoming")}
              className={cn(
                "w-10 h-10 mx-auto rounded-xl flex items-center justify-center transition-all cursor-pointer relative",
                activeView === "upcoming" && !selectedProjectId
                  ? "bg-secondary text-foreground font-semibold shadow-xs"
                  : "text-muted hover:text-foreground hover:bg-secondary/70"
              )}
              title={`Upcoming (${counts.upcoming})`}
            >
              <Calendar size={18} className="text-indigo-500" />
              {counts.upcoming > 0 && (
                <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-indigo-500" />
              )}
            </button>

            {/* Overdue */}
            {counts.overdue > 0 && (
              <button
                type="button"
                onClick={() => handleSelectView("overdue")}
                className={cn(
                  "w-10 h-10 mx-auto rounded-xl flex items-center justify-center transition-all cursor-pointer relative",
                  activeView === "overdue" && !selectedProjectId
                    ? "bg-red-500/15 text-red-600 shadow-xs"
                    : "text-red-500/80 hover:text-red-600 hover:bg-red-500/10"
                )}
                title={`Overdue (${counts.overdue})`}
              >
                <AlertCircle size={18} className="text-red-500" />
                <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-red-500" />
              </button>
            )}

            {/* AI Daily Planner */}
            <button
              type="button"
              onClick={() => {
                openDailyPlanner();
                setOpen(false);
              }}
              className="w-10 h-10 mx-auto rounded-xl flex items-center justify-center transition-all cursor-pointer relative text-purple-600 dark:text-purple-400 hover:bg-purple-500/10"
              title="Daily Planner (AI)"
            >
              <CalendarCheck size={18} className="text-purple-500" />
            </button>
          </nav>
        ) : (
          <nav className="space-y-1">
            {/* Inbox */}
            <button
              type="button"
              onClick={() => handleSelectView("inbox")}
              className={cn(
                "w-full flex items-center justify-between rounded-xl px-3 py-2 text-sm font-medium transition-all cursor-pointer",
                activeView === "inbox" && !selectedProjectId
                  ? "bg-secondary text-foreground font-semibold shadow-xs"
                  : "text-muted hover:text-foreground hover:bg-secondary/70"
              )}
            >
              <div className="flex items-center gap-3">
                <Inbox size={17} className="text-blue-500" />
                <span>Inbox</span>
              </div>
              {counts.inbox > 0 && (
                <span className="text-xs px-2 py-0.5 rounded-full bg-secondary text-muted font-medium">
                  {counts.inbox}
                </span>
              )}
            </button>

            {/* Today */}
            <button
              type="button"
              onClick={() => handleSelectView("today")}
              className={cn(
                "w-full flex items-center justify-between rounded-xl px-3 py-2 text-sm font-medium transition-all cursor-pointer",
                activeView === "today" && !selectedProjectId
                  ? "bg-secondary text-foreground font-semibold shadow-xs"
                  : "text-muted hover:text-foreground hover:bg-secondary/70"
              )}
            >
              <div className="flex items-center gap-3">
                <CalendarDays size={17} className="text-emerald-500" />
                <span>Today</span>
              </div>
              {counts.today > 0 && (
                <span
                  className={cn(
                    "text-xs px-2 py-0.5 rounded-full font-medium",
                    counts.overdue > 0
                      ? "bg-amber-500/15 text-amber-600 dark:text-amber-400 font-semibold"
                      : "bg-secondary text-muted"
                  )}
                >
                  {counts.today}
                </span>
              )}
            </button>

            {/* Upcoming */}
            <button
              type="button"
              onClick={() => handleSelectView("upcoming")}
              className={cn(
                "w-full flex items-center justify-between rounded-xl px-3 py-2 text-sm font-medium transition-all cursor-pointer",
                activeView === "upcoming" && !selectedProjectId
                  ? "bg-secondary text-foreground font-semibold shadow-xs"
                  : "text-muted hover:text-foreground hover:bg-secondary/70"
              )}
            >
              <div className="flex items-center gap-3">
                <Calendar size={17} className="text-indigo-500" />
                <span>Upcoming</span>
              </div>
              {counts.upcoming > 0 && (
                <span className="text-xs px-2 py-0.5 rounded-full bg-secondary text-muted font-medium">
                  {counts.upcoming}
                </span>
              )}
            </button>

            {/* Overdue */}
            {counts.overdue > 0 && (
              <button
                type="button"
                onClick={() => handleSelectView("overdue")}
                className={cn(
                  "w-full flex items-center justify-between rounded-xl px-3 py-2 text-sm font-medium transition-all cursor-pointer",
                  activeView === "overdue" && !selectedProjectId
                    ? "bg-red-500/10 text-red-600 dark:text-red-400 font-semibold shadow-xs"
                    : "text-red-500/80 hover:text-red-600 hover:bg-red-500/10"
                )}
              >
                <div className="flex items-center gap-3">
                  <AlertCircle size={17} className="text-red-500" />
                  <span>Overdue</span>
                </div>
                <span className="text-xs px-2 py-0.5 rounded-full bg-red-500 text-white font-semibold">
                  {counts.overdue}
                </span>
              </button>
            )}

            {/* AI Daily Planner (Eisenhower Matrix) */}
            <button
              type="button"
              onClick={() => {
                openDailyPlanner();
                setOpen(false);
              }}
              className="w-full flex items-center justify-between rounded-xl px-3 py-2 text-sm font-medium transition-all cursor-pointer text-purple-600 dark:text-purple-400 hover:bg-purple-500/10 mt-1"
            >
              <div className="flex items-center gap-3">
                <CalendarCheck size={17} className="text-purple-500" />
                <span>Daily Planner</span>
              </div>
              <span className="text-[10px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded-full bg-purple-500/10 text-purple-600 dark:text-purple-400">
                AI
              </span>
            </button>
          </nav>
        )}

        {/* Projects Section */}
        <ProjectSidebar collapsed={desktopCollapsed} />

        {/* Footer: Timezone Badge + Logout */}
        <div className="mt-auto pt-6 space-y-2">
          {desktopCollapsed ? (
            <div className="hidden md:flex flex-col items-center gap-2">
              <div
                className="w-10 h-10 rounded-xl flex items-center justify-center bg-secondary/60 border border-border/50 text-muted cursor-default"
                title={`Active Timezone: ${activeTimezone}`}
              >
                <Globe size={16} />
              </div>
              <button
                type="button"
                onClick={() => {
                  logout();
                  navigate("/login");
                }}
                className="w-10 h-10 rounded-xl flex items-center justify-center text-muted hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
                title="Logout"
              >
                <LogOut size={16} />
              </button>
            </div>
          ) : (
            <>
              <div
                className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-secondary/60 border border-border/50 text-[11px] text-muted font-medium select-none"
                title={`Active Timezone: ${activeTimezone}. All timestamps and calendar schedules synchronize to UTC.`}
              >
                <Globe size={13} className="text-muted shrink-0" />
                <span className="truncate">{activeTimezone}</span>
              </div>

              <Button
                variant="ghost"
                onClick={() => {
                  logout();
                  navigate("/login");
                }}
                className="w-full justify-start text-muted hover:text-foreground cursor-pointer"
              >
                <LogOut size={16} />
                <span className="ml-2">Logout</span>
              </Button>
            </>
          )}
        </div>
      </aside>

      {/* Backdrop for mobile sidebar */}
      {open && (
        <div
          onClick={() => setOpen(false)}
          className="fixed inset-0 bg-black/50 backdrop-blur-xs z-40 md:hidden"
        />
      )}

      {/* Main Content Area */}
      <main className="flex-1 overflow-y-auto pt-20 md:pt-0 pb-24 md:pb-6">{children}</main>

      {/* Mobile Bottom Navigation Bar */}
      <nav
        aria-label="Mobile Navigation"
        className="
          md:hidden
          fixed bottom-0 left-0 right-0
          h-16
          bg-card/95
          backdrop-blur-xl
          border-t border-border
          z-40
          flex items-center justify-around
          px-2
        "
      >
        {/* Inbox */}
        <button
          type="button"
          onClick={() => handleSelectView("inbox")}
          className={cn(
            "flex flex-col items-center justify-center flex-1 h-full py-1 text-xs font-medium transition-colors cursor-pointer relative",
            activeView === "inbox" && !selectedProjectId
              ? "text-blue-500 font-semibold"
              : "text-muted hover:text-foreground"
          )}
        >
          <div className="relative">
            <Inbox size={20} />
            {counts.inbox > 0 && (
              <span className="absolute -top-1 -right-2 min-w-4 h-4 px-1 rounded-full bg-blue-500 text-white text-[10px] flex items-center justify-center font-bold">
                {counts.inbox > 99 ? "99+" : counts.inbox}
              </span>
            )}
          </div>
          <span className="text-[10px] mt-1">Inbox</span>
        </button>

        {/* Today */}
        <button
          type="button"
          onClick={() => handleSelectView("today")}
          className={cn(
            "flex flex-col items-center justify-center flex-1 h-full py-1 text-xs font-medium transition-colors cursor-pointer relative",
            activeView === "today" && !selectedProjectId
              ? "text-emerald-500 font-semibold"
              : "text-muted hover:text-foreground"
          )}
        >
          <div className="relative">
            <CalendarDays size={20} />
            {counts.today > 0 && (
              <span
                className={cn(
                  "absolute -top-1 -right-2 min-w-4 h-4 px-1 rounded-full text-white text-[10px] flex items-center justify-center font-bold",
                  counts.overdue > 0 ? "bg-amber-500" : "bg-emerald-500"
                )}
              >
                {counts.today > 99 ? "99+" : counts.today}
              </span>
            )}
          </div>
          <span className="text-[10px] mt-1">Today</span>
        </button>

        {/* Center Quick Add (+) Button */}
        {onOpenCreateTask && (
          <div className="flex items-center justify-center px-1">
            <button
              type="button"
              onClick={onOpenCreateTask}
              className="
                w-12 h-12 rounded-full
                bg-primary text-primary-foreground
                shadow-lg shadow-primary/25
                flex items-center justify-center
                -mt-5
                hover:scale-105 active:scale-95
                cursor-pointer
                transition-all
                border-4 border-card
              "
              aria-label="Add task"
            >
              <Plus size={22} className="stroke-[2.5]" />
            </button>
          </div>
        )}

        {/* Upcoming */}
        <button
          type="button"
          onClick={() => handleSelectView("upcoming")}
          className={cn(
            "flex flex-col items-center justify-center flex-1 h-full py-1 text-xs font-medium transition-colors cursor-pointer relative",
            activeView === "upcoming" && !selectedProjectId
              ? "text-indigo-500 font-semibold"
              : "text-muted hover:text-foreground"
          )}
        >
          <div className="relative">
            <Calendar size={20} />
            {counts.upcoming > 0 && (
              <span className="absolute -top-1 -right-2 min-w-4 h-4 px-1 rounded-full bg-indigo-500 text-white text-[10px] flex items-center justify-center font-bold">
                {counts.upcoming > 99 ? "99+" : counts.upcoming}
              </span>
            )}
          </div>
          <span className="text-[10px] mt-1">Upcoming</span>
        </button>

        {/* Menu / Sidebar Drawer Trigger */}
        <button
          type="button"
          onClick={toggle}
          className={cn(
            "flex flex-col items-center justify-center flex-1 h-full py-1 text-xs font-medium transition-colors cursor-pointer",
            open ? "text-accent font-semibold" : "text-muted hover:text-foreground"
          )}
        >
          <Menu size={20} />
          <span className="text-[10px] mt-1">Menu</span>
        </button>
      </nav>
    </div>
  );
};

export default AppLayout;

