import { type ReactNode } from "react";
import { ProjectSidebar } from "../../features/projects/components/ProjectSidebar";
import { ThemeToggle } from "../ui/ThemeToggle";

type Props = {
  children: ReactNode;
};

export const AppLayout = ({ children }: Props) => {
  return (
    <div className="flex h-screen bg-background text-foreground">
      <aside className="w-72 border-r border-border bg-background/80 backdrop-blur-xl px-4 py-6">
        <div className="flex items-center justify-between mb-8">
          <h1 className="text-2xl font-bold tracking-tight">TodoFlow</h1>
          <ThemeToggle />
        </div>
        {/* <div className="mb-6">
          <ThemeToggle />
        </div> */}

        <nav className="p-2 space-y-1">
          <button className="w-full flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium hover:bg-secondary transition-all">
            Inbox
          </button>

          <button className="w-full flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium hover:bg-secondary transition-all">
            Today
          </button>

          <button className="w-full flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium hover:bg-secondary transition-all">
            Upcoming
          </button>
        </nav>
        <ProjectSidebar />
      </aside>

      <main className="flex-1 overflow-auto p-6">{children}</main>
    </div>
  );
};
