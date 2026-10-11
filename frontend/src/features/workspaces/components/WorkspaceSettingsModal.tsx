import { useState, useEffect } from "react";
import { Modal } from "../../../components/ui/Modal";
import { Input } from "../../../components/ui/Input";
import { Button } from "../../../components/ui/Button";
import type {
  WorkspaceResponse,
  WorkspaceRole,
} from "../workspaceApi";
import {
  useWorkspaceMembers,
  useUpdateWorkspace,
  useDeleteWorkspace,
  useAddWorkspaceMember,
  useUpdateWorkspaceMemberRole,
  useRemoveWorkspaceMember,
} from "../workspaceQueries";
import { authStorage } from "../../auth/authStorage";
import { useWorkspaceStore } from "../../../store/workspaceStore";
import { Users, Shield, Trash2, UserPlus, LogOut } from "lucide-react";
import toast from "react-hot-toast";

type WorkspaceSettingsModalProps = {
  isOpen: boolean;
  onClose: () => void;
  workspace: WorkspaceResponse | null;
};

export const WorkspaceSettingsModal = ({
  isOpen,
  onClose,
  workspace,
}: WorkspaceSettingsModalProps) => {
  const currentUserId = authStorage.getUserId();
  const setActiveWorkspaceId = useWorkspaceStore(
    (state) => state.setActiveWorkspaceId
  );

  const [name, setName] = useState("");
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<WorkspaceRole>("MEMBER");

  useEffect(() => {
    if (workspace) {
      setName(workspace.name);
    }
  }, [workspace]);

  const { data: members = [], isLoading: isLoadingMembers } =
    useWorkspaceMembers(workspace?.id);

  const updateWorkspaceMutation = useUpdateWorkspace();
  const deleteWorkspaceMutation = useDeleteWorkspace();
  const addMemberMutation = useAddWorkspaceMember();
  const updateRoleMutation = useUpdateWorkspaceMemberRole();
  const removeMemberMutation = useRemoveWorkspaceMember();

  if (!workspace) return null;

  const isOwner = workspace.currentUserRole === "OWNER";
  const isAdminOrOwner = isOwner || workspace.currentUserRole === "ADMIN";

  const handleUpdateName = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || name === workspace.name) return;

    updateWorkspaceMutation.mutate(
      { workspaceId: workspace.id, request: { name: name.trim() } },
      {
        onSuccess: () => {
          toast.success("Workspace updated");
        },
        onError: (err: any) => {
          toast.error(err.response?.data?.message || "Failed to update workspace");
        },
      }
    );
  };

  const handleInvite = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim()) return;

    addMemberMutation.mutate(
      {
        workspaceId: workspace.id,
        request: { email: inviteEmail.trim(), role: inviteRole },
      },
      {
        onSuccess: (newMember) => {
          toast.success(`Added ${newMember.email} as ${newMember.role}`);
          setInviteEmail("");
        },
        onError: (err: any) => {
          toast.error(err.response?.data?.message || "Failed to add member");
        },
      }
    );
  };

  const handleRoleChange = (targetUserId: string, newRole: WorkspaceRole) => {
    updateRoleMutation.mutate(
      {
        workspaceId: workspace.id,
        targetUserId,
        request: { role: newRole },
      },
      {
        onSuccess: () => {
          toast.success("Member role updated");
        },
        onError: (err: any) => {
          toast.error(err.response?.data?.message || "Failed to update role");
        },
      }
    );
  };

  const handleRemoveMember = (targetUserId: string, isSelf: boolean) => {
    const confirmMsg = isSelf
      ? "Are you sure you want to leave this workspace?"
      : "Remove this member from the workspace?";
    if (!window.confirm(confirmMsg)) return;

    removeMemberMutation.mutate(
      { workspaceId: workspace.id, targetUserId },
      {
        onSuccess: () => {
          toast.success(isSelf ? "You left the workspace" : "Member removed");
          if (isSelf) {
            setActiveWorkspaceId(null);
            onClose();
          }
        },
        onError: (err: any) => {
          toast.error(err.response?.data?.message || "Failed to remove member");
        },
      }
    );
  };

  const handleDeleteWorkspace = () => {
    if (
      !window.confirm(
        `Are you sure you want to delete workspace "${workspace.name}"? This cannot be undone.`
      )
    ) {
      return;
    }

    deleteWorkspaceMutation.mutate(workspace.id, {
      onSuccess: () => {
        toast.success("Workspace deleted");
        setActiveWorkspaceId(null);
        onClose();
      },
      onError: (err: any) => {
        toast.error(err.response?.data?.message || "Failed to delete workspace");
      },
    });
  };

  return (
    <Modal
      open={isOpen}
      onClose={onClose}
      title={`${workspace.name} — Settings & Members`}
    >
      <div className="space-y-6 max-h-[75vh] overflow-y-auto pr-1">
        {/* Workspace Name */}
        {isAdminOrOwner && (
          <form
            onSubmit={handleUpdateName}
            className="p-4 rounded-lg bg-secondary/40 border border-border space-y-3"
          >
            <label className="block text-sm font-semibold text-foreground">
              Workspace Name
            </label>
            <div className="flex gap-2">
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Workspace name"
              />
              <Button
                type="submit"
                disabled={
                  !name.trim() ||
                  name === workspace.name ||
                  updateWorkspaceMutation.isPending
                }
              >
                Save
              </Button>
            </div>
          </form>
        )}

        {/* Invite Member Section */}
        {isAdminOrOwner && (
          <div className="p-4 rounded-lg bg-secondary/40 border border-border space-y-3">
            <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <UserPlus size={16} className="text-accent" />
              <span>Invite New Member</span>
            </div>
            <form onSubmit={handleInvite} className="flex flex-col sm:flex-row gap-2">
              <Input
                type="email"
                placeholder="User email address"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                className="flex-1"
              />
              <select
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value as WorkspaceRole)}
                className="bg-card border border-border text-foreground text-sm rounded-md px-3 py-2 outline-none focus:ring-1 focus:ring-accent"
              >
                <option value="ADMIN">Admin</option>
                <option value="MEMBER">Member</option>
                <option value="VIEWER">Viewer</option>
              </select>
              <Button
                type="submit"
                disabled={!inviteEmail.trim() || addMemberMutation.isPending}
              >
                {addMemberMutation.isPending ? "Adding..." : "Invite"}
              </Button>
            </form>
          </div>
        )}

        {/* Members List */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <Users size={16} />
              Members ({members.length})
            </h3>
          </div>

          <div className="divide-y divide-border border border-border rounded-lg bg-card overflow-hidden">
            {isLoadingMembers ? (
              <div className="p-4 text-center text-sm text-muted-foreground">
                Loading members...
              </div>
            ) : members.length === 0 ? (
              <div className="p-4 text-center text-sm text-muted-foreground">
                No members found.
              </div>
            ) : (
              members.map((m) => {
                const isMemberSelf = m.userId === currentUserId;
                const isMemberOwner = m.role === "OWNER";

                return (
                  <div
                    key={m.id}
                    className="p-3 flex items-center justify-between gap-3 text-sm hover:bg-secondary/30 transition-colors"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-8 h-8 rounded-full bg-accent/15 text-accent font-semibold flex items-center justify-center shrink-0 text-xs">
                        {(m.fullName?.[0] || m.email[0]).toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <div className="font-medium text-foreground truncate flex items-center gap-1.5">
                          <span>{m.fullName || m.email}</span>
                          {isMemberSelf && (
                            <span className="text-[10px] bg-secondary text-muted-foreground px-1.5 py-0.5 rounded">
                              You
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-muted-foreground truncate">
                          {m.email}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      {isMemberOwner ? (
                        <span className="inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
                          <Shield size={12} />
                          Owner
                        </span>
                      ) : isAdminOrOwner && !isMemberSelf ? (
                        <select
                          value={m.role}
                          onChange={(e) =>
                            handleRoleChange(
                              m.userId,
                              e.target.value as WorkspaceRole
                            )
                          }
                          disabled={updateRoleMutation.isPending}
                          className="bg-card border border-border text-foreground text-xs rounded px-2 py-1 outline-none focus:ring-1 focus:ring-accent"
                        >
                          <option value="ADMIN">Admin</option>
                          <option value="MEMBER">Member</option>
                          <option value="VIEWER">Viewer</option>
                        </select>
                      ) : (
                        <span className="text-xs text-muted-foreground capitalize font-medium">
                          {m.role.toLowerCase()}
                        </span>
                      )}

                      {/* Action Button: Remove or Leave */}
                      {isMemberSelf && !isMemberOwner ? (
                        <button
                          onClick={() => handleRemoveMember(m.userId, true)}
                          title="Leave workspace"
                          className="p-1.5 text-muted-foreground hover:text-red-500 rounded transition-colors"
                        >
                          <LogOut size={16} />
                        </button>
                      ) : isAdminOrOwner && !isMemberOwner && !isMemberSelf ? (
                        <button
                          onClick={() => handleRemoveMember(m.userId, false)}
                          title="Remove member"
                          className="p-1.5 text-muted-foreground hover:text-red-500 rounded transition-colors"
                        >
                          <Trash2 size={16} />
                        </button>
                      ) : null}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Danger Zone */}
        {isOwner && (
          <div className="pt-4 border-t border-border">
            <div className="p-4 rounded-lg border border-red-500/20 bg-red-500/5 space-y-2">
              <h4 className="text-sm font-semibold text-red-600 dark:text-red-400">
                Danger Zone
              </h4>
              <p className="text-xs text-muted-foreground">
                Permanently delete this workspace and all associated projects.
              </p>
              <Button
                type="button"
                variant="danger"
                className="text-xs px-3 py-1.5"
                onClick={handleDeleteWorkspace}
                disabled={deleteWorkspaceMutation.isPending}
              >
                Delete Workspace
              </Button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
};
