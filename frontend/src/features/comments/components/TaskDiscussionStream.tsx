import { useState, useEffect, useRef } from "react";
import {
  useTaskComments,
  useTaskActivities,
  useCreateTaskComment,
  useUpdateTaskComment,
  useDeleteTaskComment,
} from "../api/commentQueries";
import type { TaskComment } from "../types/commentTypes";
import type { Activity } from "../../activity/api/activityTypes";
import { useCurrentUser } from "../../users/api/userQueries";
import { useWorkspaceStore } from "../../../store/workspaceStore";
import { useWorkspaceMembers } from "../../workspaces/workspaceQueries";
import { createStompClient } from "../../../realtime/socket";
import { formatTimeAgo } from "../../../utils/date";
import { Button } from "../../../components/ui/Button";
import {
  MessageSquare,
  History,
  Send,
  Trash2,
  Edit2,
  AtSign,
  PlusCircle,
  CheckCircle2,
  Clock,
  Loader2,
} from "lucide-react";
import toast from "react-hot-toast";
import { cn } from "../../../lib/cn";
import { useQueryClient } from "@tanstack/react-query";

type Props = {
  taskId: string;
};

export const TaskDiscussionStream = ({ taskId }: Props) => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<"comments" | "activity">("comments");
  const [content, setContent] = useState("");
  const [editingCommentId, setEditingCommentId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState("");
  const [showMentionMenu, setShowMentionMenu] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const { data: comments = [], isLoading: isLoadingComments } = useTaskComments(taskId);
  const { data: activities = [], isLoading: isLoadingActivities } = useTaskActivities(taskId);
  const { data: currentUser } = useCurrentUser();

  const activeWorkspaceId = useWorkspaceStore((state) => state.activeWorkspaceId);
  const { data: members = [] } = useWorkspaceMembers(activeWorkspaceId ?? undefined);

  const createCommentMutation = useCreateTaskComment(taskId);
  const updateCommentMutation = useUpdateTaskComment(taskId);
  const deleteCommentMutation = useDeleteTaskComment(taskId);

  // Subscribe to real-time comments over STOMP WebSocket
  useEffect(() => {
    if (!taskId) return;

    let client: ReturnType<typeof createStompClient> | null = null;
    try {
      client = createStompClient({
        onConnect: () => {
          client?.subscribe(`/topic/tasks/${taskId}/comments`, (message) => {
            try {
              const incoming: TaskComment = JSON.parse(message.body);
              queryClient.setQueryData<TaskComment[]>(
                ["tasks", taskId, "comments"],
                (prev = []) => {
                  if (prev.some((c) => c.id === incoming.id)) {
                    return prev.map((c) => (c.id === incoming.id ? incoming : c));
                  }
                  return [...prev, incoming];
                }
              );
            } catch {
              // Ignore malformed payloads
            }
          });
        },
      });
      client.activate();
    } catch {
      // Fallback cleanly if WS is unreachable
    }

    return () => {
      if (client && client.active) {
        client.deactivate();
      }
    };
  }, [taskId, queryClient]);

  const handleSubmitComment = () => {
    if (!content.trim()) return;

    createCommentMutation.mutate(content.trim(), {
      onSuccess: () => {
        setContent("");
        setShowMentionMenu(false);
        toast.success("Comment added");
      },
      onError: () => {
        toast.error("Failed to add comment");
      },
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      handleSubmitComment();
    }
  };

  const handleInsertMention = (name: string) => {
    const formatted = `@${name.replace(/\s+/g, "")} `;
    setContent((prev) => prev + formatted);
    setShowMentionMenu(false);
    textareaRef.current?.focus();
  };

  const handleSaveEdit = (commentId: string) => {
    if (!editingContent.trim()) {
      toast.error("Comment cannot be empty");
      return;
    }

    updateCommentMutation.mutate(
      { commentId, content: editingContent.trim() },
      {
        onSuccess: () => {
          setEditingCommentId(null);
          setEditingContent("");
          toast.success("Comment updated");
        },
        onError: () => {
          toast.error("Failed to update comment");
        },
      }
    );
  };

  const handleDeleteComment = (commentId: string) => {
    if (window.confirm("Are you sure you want to delete this comment?")) {
      deleteCommentMutation.mutate(commentId, {
        onSuccess: () => {
          toast.success("Comment deleted");
        },
        onError: () => {
          toast.error("Failed to delete comment");
        },
      });
    }
  };

  const renderCommentContent = (text: string) => {
    // Highlight @Mentions
    const parts = text.split(/(@[a-zA-Z0-9_\-]+)/g);
    return (
      <span className="whitespace-pre-wrap break-words">
        {parts.map((part, index) => {
          if (part.startsWith("@")) {
            return (
              <span
                key={index}
                className="inline-flex items-center gap-0.5 px-1 py-0.2 rounded bg-accent/15 text-accent font-medium text-[11px]"
              >
                {part}
              </span>
            );
          }
          return <span key={index}>{part}</span>;
        })}
      </span>
    );
  };

  const getActivityIcon = (type: string, message: string) => {
    const lower = message.toLowerCase();
    if (type === "TASK_COMMENTED" || lower.includes("comment")) {
      return <MessageSquare size={13} className="text-accent shrink-0 mt-0.5" />;
    }
    if (lower.includes("complete")) {
      return <CheckCircle2 size={13} className="text-emerald-500 shrink-0 mt-0.5" />;
    }
    if (lower.includes("created")) {
      return <PlusCircle size={13} className="text-blue-500 shrink-0 mt-0.5" />;
    }
    return <Clock size={13} className="text-muted shrink-0 mt-0.5" />;
  };

  return (
    <div className="pt-4 border-t border-border/70 space-y-3">
      {/* Tab Switcher Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setActiveTab("comments")}
            className={cn(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer",
              activeTab === "comments"
                ? "bg-secondary text-foreground shadow-xs"
                : "text-muted hover:text-foreground"
            )}
          >
            <MessageSquare size={13} />
            <span>Discussion</span>
            <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-card border border-border">
              {comments.length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab("activity")}
            className={cn(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer",
              activeTab === "activity"
                ? "bg-secondary text-foreground shadow-xs"
                : "text-muted hover:text-foreground"
            )}
          >
            <History size={13} />
            <span>Activity Trail</span>
            <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-card border border-border">
              {activities.length}
            </span>
          </button>
        </div>
      </div>

      {/* Tab 1: Discussion & Comments */}
      {activeTab === "comments" && (
        <div className="space-y-3">
          {/* Comments List */}
          {isLoadingComments ? (
            <div className="py-6 text-center text-xs text-muted italic flex items-center justify-center gap-2">
              <Loader2 size={14} className="animate-spin" />
              <span>Loading discussion...</span>
            </div>
          ) : comments.length === 0 ? (
            <div className="p-4 rounded-xl border border-dashed border-border/80 text-center text-muted space-y-1">
              <MessageSquare size={20} className="mx-auto text-muted/40" />
              <p className="text-xs font-medium">No comments yet</p>
              <p className="text-[11px] text-muted/70">
                Leave updates, notes, or collaborate with team members using @mentions.
              </p>
            </div>
          ) : (
            <div className="space-y-2.5 max-h-64 overflow-y-auto pr-1">
              {comments.map((comment) => {
                const isAuthor =
                  currentUser &&
                  (currentUser.id === comment.userId ||
                    currentUser.email === comment.userEmail);
                const isEditing = editingCommentId === comment.id;

                return (
                  <div
                    key={comment.id}
                    className="p-3 rounded-xl bg-card border border-border/70 hover:border-border transition-all space-y-1.5 group"
                  >
                    {/* Comment Header */}
                    <div className="flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2">
                        <div className="w-5 h-5 rounded-full bg-accent/15 text-accent font-semibold text-[10px] flex items-center justify-center uppercase">
                          {comment.userName
                            ? comment.userName.charAt(0)
                            : comment.userEmail.charAt(0)}
                        </div>
                        <span className="font-semibold text-foreground text-[11px]">
                          {comment.userName || comment.userEmail}
                        </span>
                        <span className="text-[10px] text-muted">
                          {formatTimeAgo(comment.createdAt)}
                        </span>
                        {comment.updatedAt &&
                          comment.updatedAt !== comment.createdAt && (
                            <span className="text-[9px] text-muted italic">
                              (edited)
                            </span>
                          )}
                      </div>

                      {/* Author Controls */}
                      {isAuthor && !isEditing && (
                        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            type="button"
                            onClick={() => {
                              setEditingCommentId(comment.id);
                              setEditingContent(comment.content);
                            }}
                            className="p-1 rounded text-muted hover:text-foreground hover:bg-secondary cursor-pointer"
                            title="Edit comment"
                          >
                            <Edit2 size={12} />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDeleteComment(comment.id)}
                            className="p-1 rounded text-muted hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30 cursor-pointer"
                            title="Delete comment"
                          >
                            <Trash2 size={12} />
                          </button>
                        </div>
                      )}
                    </div>

                    {/* Comment Body / Inline Editor */}
                    {isEditing ? (
                      <div className="space-y-2 pt-1">
                        <textarea
                          value={editingContent}
                          onChange={(e) => setEditingContent(e.target.value)}
                          rows={2}
                          className="w-full text-xs p-2 rounded-lg bg-background border border-border focus:ring-1 focus:ring-accent outline-hidden resize-none"
                        />
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            type="button"
                            variant="ghost"
                            onClick={() => setEditingCommentId(null)}
                            className="text-[11px] py-1 px-2"
                          >
                            Cancel
                          </Button>
                          <Button
                            type="button"
                            variant="primary"
                            onClick={() => handleSaveEdit(comment.id)}
                            disabled={updateCommentMutation.isPending}
                            className="text-[11px] py-1 px-2.5"
                          >
                            {updateCommentMutation.isPending ? "Saving..." : "Save"}
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <div className="text-xs text-foreground/90 pl-7 leading-relaxed">
                        {renderCommentContent(comment.content)}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* New Comment Input Box */}
          <div className="space-y-2 pt-1">
            <div className="relative">
              <textarea
                ref={textareaRef}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Write a comment or note... Press Ctrl+Enter to send"
                rows={2}
                className="w-full text-xs p-2.5 rounded-xl bg-card border border-border focus:ring-2 focus:ring-accent/20 focus:border-accent outline-hidden transition-all resize-none placeholder:text-muted/60"
              />

              {/* Mentions helper dropdown */}
              {showMentionMenu && members.length > 0 && (
                <div className="absolute bottom-full left-0 mb-1 w-64 bg-card border border-border rounded-xl shadow-lg p-1.5 z-20 space-y-1">
                  <div className="text-[10px] font-semibold text-muted px-2 py-0.5 uppercase tracking-wider">
                    Mention Workspace Teammate
                  </div>
                  <div className="max-h-36 overflow-y-auto space-y-0.5">
                    {members.map((member) => (
                      <button
                        key={member.id}
                        type="button"
                        onClick={() => handleInsertMention(member.fullName || member.email)}
                        className="w-full flex items-center gap-2 px-2 py-1.5 text-xs text-left rounded-lg hover:bg-secondary transition-colors cursor-pointer"
                      >
                        <div className="w-5 h-5 rounded-full bg-accent/15 text-accent text-[10px] font-semibold flex items-center justify-center uppercase">
                          {(member.fullName || member.email).charAt(0)}
                        </div>
                        <div className="truncate">
                          <span className="font-medium text-foreground">
                            {member.fullName || member.email}
                          </span>
                        </div>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Quick Actions & Send Bar */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                {members.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setShowMentionMenu((prev) => !prev)}
                    className="inline-flex items-center gap-1 px-2 py-1 rounded-md text-[11px] font-medium text-muted hover:text-foreground hover:bg-secondary border border-border/50 transition-colors cursor-pointer"
                    title="Mention team member"
                  >
                    <AtSign size={12} className="text-accent" />
                    <span>Mention teammate</span>
                  </button>
                )}
                <span className="text-[10px] text-muted hidden sm:inline">
                  Ctrl+Enter to post
                </span>
              </div>

              <Button
                type="button"
                variant="primary"
                onClick={handleSubmitComment}
                disabled={!content.trim() || createCommentMutation.isPending}
                className="text-xs py-1.5 px-3 gap-1.5"
              >
                {createCommentMutation.isPending ? (
                  <Loader2 size={13} className="animate-spin" />
                ) : (
                  <Send size={13} />
                )}
                <span>Comment</span>
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Activity Trail */}
      {activeTab === "activity" && (
        <div className="space-y-2">
          {isLoadingActivities ? (
            <div className="py-6 text-center text-xs text-muted italic flex items-center justify-center gap-2">
              <Loader2 size={14} className="animate-spin" />
              <span>Loading audit trail...</span>
            </div>
          ) : activities.length === 0 ? (
            <div className="p-4 rounded-xl border border-dashed border-border/80 text-center text-muted space-y-1">
              <History size={20} className="mx-auto text-muted/40" />
              <p className="text-xs font-medium">No activity history recorded</p>
              <p className="text-[11px] text-muted/70">
                Updates and changes to this task will be logged here.
              </p>
            </div>
          ) : (
            <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
              {activities.map((act: Activity) => (
                <div
                  key={act.id}
                  className="flex items-start gap-2.5 p-2.5 rounded-xl bg-secondary/25 border border-border/50 text-xs"
                >
                  {getActivityIcon(act.type, act.message)}
                  <div className="flex-1 min-w-0">
                    <p className="text-foreground text-[11px] leading-snug">
                      {act.message}
                    </p>
                    <span className="text-[10px] text-muted block mt-0.5">
                      {act.createdAt ? formatTimeAgo(act.createdAt) : "Just now"}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default TaskDiscussionStream;
