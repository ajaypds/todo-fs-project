import { type ReactNode } from "react";
import { ProjectSidebar } from "../../features/projects/components/ProjectSidebar";
import { ThemeToggle } from "../ui/ThemeToggle";
import { LogOut } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { Button } from "../ui/Button";

type Props = {
  children: ReactNode;
};

export const AppLayout = ({ children }: Props) => {
  const navigate = useNavigate();
  const logout = useAuthStore((state) => state.logout);

  return (
    <div className="flex h-screen bg-background text-foreground">
      <aside className="w-72 border-r border-border bg-background/80 backdrop-blur-xl px-4 py-6 flex flex-col">
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
        <div className="mt-auto pt-6">
          <Button
            variant="ghost"
            onClick={() => {
              logout();

              navigate("/login");
            }}
            className="w-full justify-start text-muted"
          >
            <LogOut size={16} />

            <span className="ml-2">Logout</span>
          </Button>
        </div>
      </aside>

      <main className="flex-1 overflow-auto p-6">{children}</main>
    </div>
  );
};
