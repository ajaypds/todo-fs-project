import { useState, useEffect, useCallback } from "react";
import { Modal } from "../../../components/ui/Modal";
import { useApiKeys, useCreateApiKey, useRevokeApiKey } from "../apiKeyQueries";
import { Button } from "../../../components/ui/Button";
import { Input } from "../../../components/ui/Input";
import { KeyRound, Copy, Check, Trash2, Terminal, Sparkles, AlertTriangle, X } from "lucide-react";
import toast from "react-hot-toast";
import { formatDate } from "../../../utils/date";

type Props = {
  open: boolean;
  onClose: () => void;
};

export const ApiKeysModal = ({ open, onClose }: Props) => {
  const { data: keys = [], isLoading } = useApiKeys(open);
  const createKeyMutation = useCreateApiKey();
  const revokeKeyMutation = useRevokeApiKey();

  const [keyName, setKeyName] = useState("");
  const [newlyGeneratedKey, setNewlyGeneratedKey] = useState<string | null>(null);
  const [newlyGeneratedKeyId, setNewlyGeneratedKeyId] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState(false);
  const [activeTab, setActiveTab] = useState<"claude" | "cursor" | "chatgpt" | "gemini">("claude");
  const [copiedConfig, setCopiedConfig] = useState(false);

  // Automatically clear one-time secret key whenever dialog is closed or reopened
  useEffect(() => {
    if (!open) {
      setNewlyGeneratedKey(null);
      setNewlyGeneratedKeyId(null);
      setKeyName("");
      setCopiedKey(false);
      setCopiedConfig(false);
    }
  }, [open]);

  // If the active keys list updates and no longer contains newlyGeneratedKeyId (e.g. deleted), clear it immediately
  useEffect(() => {
    if (newlyGeneratedKeyId && !keys.some((k) => k.id === newlyGeneratedKeyId)) {
      setNewlyGeneratedKey(null);
      setNewlyGeneratedKeyId(null);
    }
  }, [keys, newlyGeneratedKeyId]);

  const handleClose = useCallback(() => {
    setNewlyGeneratedKey(null);
    setNewlyGeneratedKeyId(null);
    setKeyName("");
    setCopiedKey(false);
    setCopiedConfig(false);
    onClose();
  }, [onClose]);

  const handleGenerate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyName.trim()) return;

    createKeyMutation.mutate(keyName.trim(), {
      onSuccess: (data) => {
        if (data.apiKey) {
          setNewlyGeneratedKey(data.apiKey);
          setNewlyGeneratedKeyId(data.id);
        }
        setKeyName("");
        toast.success("API key generated successfully!");
      },
    });
  };

  const handleRevokeKey = (keyId: string) => {
    revokeKeyMutation.mutate(keyId, {
      onSuccess: () => {
        if (newlyGeneratedKeyId === keyId) {
          setNewlyGeneratedKey(null);
          setNewlyGeneratedKeyId(null);
        }
      },
    });
  };

  const handleCopyKey = () => {
    if (!newlyGeneratedKey) return;
    navigator.clipboard.writeText(newlyGeneratedKey);
    setCopiedKey(true);
    toast.success("API key copied to clipboard");
    setTimeout(() => setCopiedKey(false), 2000);
  };

  const currentDisplayKey = newlyGeneratedKey || "todo_live_your_api_key_here";
  const apiBaseUrl = typeof window !== "undefined" ? window.location.origin : "http://localhost:8080";

  const getClaudeConfig = () => {
    return JSON.stringify(
      {
        mcpServers: {
          todoflow: {
            command: "node",
            args: ["<PATH_TO_TODO_APP>/mcp-server/dist/index.js"],
            env: {
              TODOFLOW_BASE_URL: apiBaseUrl,
              TODOFLOW_API_KEY: currentDisplayKey,
            },
          },
        },
      },
      null,
      2
    );
  };

  const getCursorConfig = () => {
    return JSON.stringify(
      {
        mcpServers: {
          todoflow: {
            command: "node",
            args: ["<PATH_TO_TODO_APP>/mcp-server/dist/index.js"],
            env: {
              TODOFLOW_BASE_URL: apiBaseUrl,
              TODOFLOW_API_KEY: currentDisplayKey,
            },
          },
        },
      },
      null,
      2
    );
  };

  const handleCopyConfig = (config: string) => {
    navigator.clipboard.writeText(config);
    setCopiedConfig(true);
    toast.success("Configuration copied to clipboard!");
    setTimeout(() => setCopiedConfig(false), 2000);
  };

  return (
    <Modal open={open} onClose={handleClose} title="🔑 API Keys & MCP Integration" maxWidth="max-w-2xl">
      <div className="space-y-6 text-sm">
        <p className="text-muted text-xs leading-relaxed">
          Generate API keys to connect <strong>Claude Desktop</strong>, <strong>ChatGPT</strong>,{" "}
          <strong>Gemini</strong>, or <strong>Cursor</strong> via the Model Context Protocol (MCP).
          When external AI models create or update tasks, your TodoFlow dashboard updates live in real time.
        </p>

        {/* Generate Key Input */}
        <form onSubmit={handleGenerate} className="flex gap-2 items-center">
          <Input
            value={keyName}
            onChange={(e) => setKeyName(e.target.value)}
            placeholder="Key label (e.g. Claude Desktop, ChatGPT, Work Laptop)..."
            className="flex-1 text-xs"
          />
          <Button
            type="submit"
            disabled={!keyName.trim() || createKeyMutation.isPending}
            className="text-xs h-9 px-4 gap-1.5 shrink-0"
          >
            <KeyRound size={14} />
            <span>{createKeyMutation.isPending ? "Generating..." : "Generate Key"}</span>
          </Button>
        </form>

        {/* Newly Generated Secret Banner */}
        {newlyGeneratedKey && (
          <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 space-y-2 relative">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-amber-700 dark:text-amber-400 font-semibold text-xs">
                <AlertTriangle size={15} />
                <span>Copy your new API Key now</span>
              </div>
              <button
                type="button"
                onClick={() => {
                  setNewlyGeneratedKey(null);
                  setNewlyGeneratedKeyId(null);
                }}
                className="text-muted hover:text-foreground p-1 rounded-md transition-colors cursor-pointer"
                title="Dismiss secret banner"
              >
                <X size={14} />
              </button>
            </div>
            <p className="text-[11px] text-muted">
              For security, this secret key will only be shown once now. It will never be shown again once you close this dialog or dismiss this banner.
            </p>
            <div className="flex items-center gap-2 mt-2">
              <code className="flex-1 p-2 bg-card border border-border rounded-lg text-xs font-mono font-semibold select-all truncate text-foreground">
                {newlyGeneratedKey}
              </code>
              <Button
                type="button"
                variant="secondary"
                onClick={handleCopyKey}
                className="text-xs h-8 px-3 gap-1 shrink-0"
              >
                {copiedKey ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
                <span>{copiedKey ? "Copied" : "Copy"}</span>
              </Button>
            </div>
            <div className="flex justify-end pt-1">
              <button
                type="button"
                onClick={() => {
                  setNewlyGeneratedKey(null);
                  setNewlyGeneratedKeyId(null);
                }}
                className="text-[11px] font-medium text-amber-800 dark:text-amber-300 hover:underline cursor-pointer"
              >
                I've saved my key (dismiss banner)
              </button>
            </div>
          </div>
        )}

        {/* Existing Keys Table */}
        <div className="space-y-2">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Active API Keys</h3>
          {isLoading ? (
            <p className="text-xs text-muted italic">Loading keys...</p>
          ) : keys.length === 0 ? (
            <div className="p-4 rounded-xl bg-secondary/30 border border-border text-center text-xs text-muted">
              No API keys generated yet. Create one above to get started.
            </div>
          ) : (
            <div className="space-y-1.5 max-h-48 overflow-y-auto">
              {keys.map((k) => (
                <div
                  key={k.id}
                  className="flex items-center justify-between p-2.5 rounded-lg bg-card border border-border/80 text-xs"
                >
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-foreground">{k.name}</span>
                      <code className="text-[10px] px-1.5 py-0.5 rounded bg-secondary text-muted font-mono">
                        {k.keyPrefix}...
                      </code>
                    </div>
                    <div className="text-[11px] text-muted flex items-center gap-3">
                      <span>Created {formatDate(k.createdAt)}</span>
                      {k.lastUsedAt && <span>Last used {formatDate(k.lastUsedAt)}</span>}
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => handleRevokeKey(k.id)}
                    disabled={revokeKeyMutation.isPending}
                    className="text-red-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 p-1.5 h-8 w-8 cursor-pointer"
                    title="Revoke key"
                  >
                    <Trash2 size={14} />
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* MCP Client Instructions Tabs */}
        <div className="pt-2 border-t border-border space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-muted flex items-center gap-1.5">
              <Sparkles size={13} className="text-accent" />
              <span>Connect with External AI Assistants</span>
            </h3>

            <div className="flex gap-1 bg-secondary/60 p-0.5 rounded-lg">
              <button
                type="button"
                onClick={() => setActiveTab("claude")}
                className={`px-2 py-1 rounded-md text-[11px] font-medium transition-colors cursor-pointer ${
                  activeTab === "claude" ? "bg-card text-foreground shadow-xs font-semibold" : "text-muted hover:text-foreground"
                }`}
              >
                Claude Desktop
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("cursor")}
                className={`px-2 py-1 rounded-md text-[11px] font-medium transition-colors cursor-pointer ${
                  activeTab === "cursor" ? "bg-card text-foreground shadow-xs font-semibold" : "text-muted hover:text-foreground"
                }`}
              >
                Cursor
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("gemini")}
                className={`px-2 py-1 rounded-md text-[11px] font-medium transition-colors cursor-pointer ${
                  activeTab === "gemini" ? "bg-card text-foreground shadow-xs font-semibold" : "text-muted hover:text-foreground"
                }`}
              >
                Gemini
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("chatgpt")}
                className={`px-2 py-1 rounded-md text-[11px] font-medium transition-colors cursor-pointer ${
                  activeTab === "chatgpt" ? "bg-card text-foreground shadow-xs font-semibold" : "text-muted hover:text-foreground"
                }`}
              >
                ChatGPT
              </button>
            </div>
          </div>

          {activeTab === "claude" && (
            <div className="space-y-2">
              <p className="text-[11px] text-muted">
                Add to your <code>claude_desktop_config.json</code>:
              </p>
              <div className="relative">
                <pre className="p-3 bg-secondary/80 rounded-xl font-mono text-[11px] overflow-x-auto text-foreground/90 border border-border/80">
                  {getClaudeConfig()}
                </pre>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => handleCopyConfig(getClaudeConfig())}
                  className="absolute top-2 right-2 text-xs h-7 px-2.5 gap-1 bg-card/90 hover:bg-card"
                >
                  {copiedConfig ? <Check size={12} className="text-emerald-500" /> : <Copy size={12} />}
                  <span>{copiedConfig ? "Copied" : "Copy JSON"}</span>
                </Button>
              </div>
            </div>
          )}

          {activeTab === "cursor" && (
            <div className="space-y-2">
              <p className="text-[11px] text-muted">
                Go to <strong>Cursor Settings → Features → MCP Servers</strong> and add:
              </p>
              <div className="relative">
                <pre className="p-3 bg-secondary/80 rounded-xl font-mono text-[11px] overflow-x-auto text-foreground/90 border border-border/80">
                  {getCursorConfig()}
                </pre>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => handleCopyConfig(getCursorConfig())}
                  className="absolute top-2 right-2 text-xs h-7 px-2.5 gap-1 bg-card/90 hover:bg-card"
                >
                  {copiedConfig ? <Check size={12} className="text-emerald-500" /> : <Copy size={12} />}
                  <span>{copiedConfig ? "Copied" : "Copy JSON"}</span>
                </Button>
              </div>
            </div>
          )}

          {activeTab === "gemini" && (
            <div className="p-3 bg-secondary/60 rounded-xl border border-border text-[11px] space-y-2 text-muted">
              <div className="flex items-center gap-1.5 font-semibold text-foreground">
                <Terminal size={14} className="text-blue-500" />
                <span>Gemini CLI / Google AI Studio Integration</span>
              </div>
              <p>
                Configure Gemini using the standard MCP Stdio transport by pointing to <code>mcp-server/dist/index.js</code> with environment variables:
              </p>
              <code className="block p-2 bg-card rounded font-mono text-[10px] text-foreground border border-border/70 select-all">
                TODOFLOW_BASE_URL={apiBaseUrl} TODOFLOW_API_KEY={currentDisplayKey} node mcp-server/dist/index.js
              </code>
            </div>
          )}

          {activeTab === "chatgpt" && (
            <div className="p-3 bg-secondary/60 rounded-xl border border-border text-[11px] space-y-2 text-muted">
              <div className="flex items-center gap-1.5 font-semibold text-foreground">
                <Sparkles size={14} className="text-emerald-500" />
                <span>ChatGPT Custom Actions & HTTP Integration</span>
              </div>
              <p>
                In <strong>Custom GPT → Configure → Actions</strong>, use the TodoFlow REST API with API Key authentication:
              </p>
              <ul className="list-disc pl-4 space-y-1">
                <li>Authentication: <strong>API Key</strong></li>
                <li>Header Name: <code>X-API-Key</code></li>
                <li>API Key: <code>{currentDisplayKey}</code></li>
              </ul>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
};

export default ApiKeysModal;
