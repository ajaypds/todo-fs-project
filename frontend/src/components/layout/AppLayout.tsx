import { type ReactNode } from "react";
import { ProjectSidebar } from "../../features/projects/components/ProjectSidebar";
import { ThemeToggle } from "../ui/ThemeToggle";
import { LogOut } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { Button } from "../ui/Button";
import { Menu, X } from "lucide-react";
import { useSidebarStore } from "../../store/sidebarStore";
import { cn } from "../../lib/cn";

type Props = {
  children: ReactNode;
};

export const AppLayout = ({ children }: Props) => {
  const navigate = useNavigate();
  const logout = useAuthStore((state) => state.logout);
  const { open, toggle, setOpen } = useSidebarStore();

  return (
    <div className="flex h-screen bg-background text-foreground">
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
        <h1 className="font-bold">TodoFlow</h1>

        <button onClick={toggle}>
          {open ? <X size={22} /> : <Menu size={22} />}
        </button>
      </div>
      {/* <aside className="w-72 border-r border-border bg-background/80 backdrop-blur-xl px-4 py-6 flex flex-col"> */}
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

          open ? "translate-x-0" : "-translate-x-full md:translate-x-0",
        )}
      >
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
      {open && (
        <div
          onClick={() => setOpen(false)}
          className="
        fixed inset-0
        bg-black/40
        z-40 md:hidden
      "
        />
      )}

      <main className="flex-1 overflow-y-auto pt-20 md:pt-0">{children}</main>
    </div>
  );
};
