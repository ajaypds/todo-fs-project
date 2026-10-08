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
};

export const AppLayout = ({ children }: Props) => {
  const navigate = useNavigate();
  const token = useAuthStore((state) => state.token);
  const logout = useAuthStore((state) => state.logout);
  const { open, toggle, setOpen } = useSidebarStore();
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
            w-72
            border-r border-border
            bg-card/90
            backdrop-blur-xl
            px-4 py-6
            transition-transform
            duration-300
            flex flex-col
          `,
          open ? "translate-x-0" : "-translate-x-full md:translate-x-0"
        )}
      >
        <div className="flex items-center justify-between mb-6 px-2">
          <h1 className="text-2xl font-bold tracking-tight">TodoFlow</h1>
          <div className="hidden md:flex items-center gap-2">
            <RealtimeStatusIndicator />
            <ThemeToggle />
          </div>
        </div>

        {/* Command Palette Quick Trigger Button */}
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

        {/* Smart Views Navigation */}
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

          {/* Overdue (Highlighted if overdue count > 0) */}
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

        {/* Projects Section */}
        <ProjectSidebar />

        {/* Footer: Timezone Badge + Logout */}
        <div className="mt-auto pt-6 space-y-2">
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
        </div>
      </aside>

      {/* Backdrop for mobile sidebar */}
      {open && (
        <div
          onClick={() => setOpen(false)}
          className="fixed inset-0 bg-black/40 z-40 md:hidden"
        />
      )}

      {/* Main Content Area */}
      <main className="flex-1 overflow-y-auto pt-20 md:pt-0">{children}</main>
    </div>
  );
};

export default AppLayout;
