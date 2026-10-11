import { useState, useRef, useEffect } from "react";
import { useWorkspaces } from "../workspaceQueries";
import { useWorkspaceStore } from "../../../store/workspaceStore";
import { CreateWorkspaceModal } from "./CreateWorkspaceModal";
import { WorkspaceSettingsModal } from "./WorkspaceSettingsModal";
import {
  ChevronDown,
  Check,
  Plus,
  Settings,
  Building2,
} from "lucide-react";

export const WorkspaceSwitcher = () => {
  const { data: workspaces = [], isLoading } = useWorkspaces();
  const activeWorkspaceId = useWorkspaceStore(
    (state) => state.activeWorkspaceId
  );
  const setActiveWorkspaceId = useWorkspaceStore(
    (state) => state.setActiveWorkspaceId
  );

  const [isOpen, setIsOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Auto-select first workspace if none active or invalid
  useEffect(() => {
    if (workspaces.length > 0) {
      const exists = workspaces.some((w) => w.id === activeWorkspaceId);
      if (!activeWorkspaceId || !exists) {
        setActiveWorkspaceId(workspaces[0].id);
      }
    }
  }, [workspaces, activeWorkspaceId, setActiveWorkspaceId]);

  const activeWorkspace =
    workspaces.find((w) => w.id === activeWorkspaceId) || workspaces[0];

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node)
      ) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  if (isLoading || !activeWorkspace) {
    return (
      <div className="px-3 py-2 text-xs text-muted-foreground animate-pulse">
        Loading workspace...
      </div>
    );
  }

  const roleBadgeColors: Record<string, string> = {
    OWNER: "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20",
    ADMIN: "bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20",
    MEMBER: "bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20",
    VIEWER: "bg-gray-500/10 text-gray-600 dark:text-gray-400 border-gray-500/20",
  };

  return (
    <div className="relative px-3 py-2" ref={dropdownRef}>
      {/* Trigger Button */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="
          w-full flex items-center justify-between gap-2.5 px-3 py-2
          rounded-lg border border-border/80 bg-secondary/40 hover:bg-secondary/70
          text-foreground transition-all duration-150 text-left cursor-pointer
        "
      >
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="w-7 h-7 rounded-md bg-accent text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-sm">
            {activeWorkspace.name.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0 flex flex-col">
            <span className="text-xs font-semibold truncate leading-tight">
              {activeWorkspace.name}
            </span>
            <span
              className={`
                mt-0.5 inline-block text-[9px] font-medium uppercase px-1.5 py-0.2 rounded border w-fit leading-tight
                ${roleBadgeColors[activeWorkspace.currentUserRole] || roleBadgeColors.VIEWER}
              `}
            >
              {activeWorkspace.currentUserRole.toLowerCase()}
            </span>
          </div>
        </div>
        <ChevronDown
          size={14}
          className={`text-muted-foreground shrink-0 transition-transform duration-200 ${
            isOpen ? "rotate-180" : ""
          }`}
        />
      </button>

      {/* Dropdown Menu */}
      {isOpen && (
        <div
          className="
            absolute left-3 right-3 top-full mt-1.5 z-50
            bg-card border border-border rounded-xl shadow-xl
            p-1.5 text-sm space-y-1 animate-in fade-in zoom-in-95 duration-100
          "
        >
          <div className="px-2.5 py-1 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
            Workspaces
          </div>

          <div className="max-h-48 overflow-y-auto space-y-0.5">
            {workspaces.map((w) => {
              const isSelected = w.id === activeWorkspace.id;
              return (
                <button
                  key={w.id}
                  onClick={() => {
                    setActiveWorkspaceId(w.id);
                    setIsOpen(false);
                  }}
                  className={`
                    w-full flex items-center justify-between gap-2 px-2.5 py-2 rounded-lg text-left text-xs transition-colors cursor-pointer
                    ${
                      isSelected
                        ? "bg-accent/10 text-accent font-medium"
                        : "text-foreground hover:bg-secondary"
                    }
                  `}
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <Building2 size={14} className="shrink-0" />
                    <span className="truncate">{w.name}</span>
                  </div>
                  {isSelected && <Check size={14} className="shrink-0" />}
                </button>
              );
            })}
          </div>

          <div className="pt-1 mt-1 border-t border-border space-y-0.5">
            <button
              onClick={() => {
                setIsOpen(false);
                setIsSettingsOpen(true);
              }}
              className="
                w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs
                text-foreground hover:bg-secondary transition-colors cursor-pointer
              "
            >
              <Settings size={14} className="text-muted-foreground" />
              <span>Workspace Settings & Members</span>
            </button>

            <button
              onClick={() => {
                setIsOpen(false);
                setIsCreateOpen(true);
              }}
              className="
                w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs
                text-foreground hover:bg-secondary transition-colors cursor-pointer
              "
            >
              <Plus size={14} className="text-muted-foreground" />
              <span>Create New Workspace</span>
            </button>
          </div>
        </div>
      )}

      {/* Modals */}
      <CreateWorkspaceModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />

      <WorkspaceSettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        workspace={activeWorkspace}
      />
    </div>
  );
};
