import { useState, useRef, type ChangeEvent } from "react";
import { cn } from "../../lib/cn";
import { MarkdownViewer } from "./MarkdownViewer";
import {
  Bold,
  Italic,
  Strikethrough,
  Heading2,
  List,
  ListOrdered,
  CheckSquare,
  Code,
  Link as LinkIcon,
  Quote,
  Eye,
  PenLine,
} from "lucide-react";

type Props = {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  minHeight?: string;
  defaultMode?: "write" | "preview";
  label?: string;
};

export const MarkdownEditor = ({
  value,
  onChange,
  placeholder = "Write description or notes in Markdown...",
  className,
  minHeight = "min-h-[140px]",
  defaultMode = "write",
  label,
}: Props) => {
  const [mode, setMode] = useState<"write" | "preview">(defaultMode);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Helper to insert or wrap markdown formatting around cursor or selection
  const insertFormat = (
    prefix: string,
    suffix: string = "",
    defaultText: string = ""
  ) => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = value.substring(start, end) || defaultText;

    const replacement = `${prefix}${selectedText}${suffix}`;
    const newValue =
      value.substring(0, start) + replacement + value.substring(end);

    onChange(newValue);

    // Reposition cursor and restore focus
    requestAnimationFrame(() => {
      textarea.focus();
      const newCursorPos = selectedText
        ? start + prefix.length + selectedText.length + suffix.length
        : start + prefix.length;
      textarea.setSelectionRange(
        selectedText ? newCursorPos : start + prefix.length,
        selectedText ? newCursorPos : start + prefix.length + defaultText.length
      );
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Formatting keyboard shortcuts
    if (e.ctrlKey || e.metaKey) {
      if (e.key.toLowerCase() === "b") {
        e.preventDefault();
        insertFormat("**", "**", "bold text");
      } else if (e.key.toLowerCase() === "i") {
        e.preventDefault();
        insertFormat("*", "*", "italic text");
      }
    }
  };

  return (
    <div className={cn("space-y-1.5", className)}>
      {/* Header bar: optional label + Write/Preview mode tabs */}
      <div className="flex items-center justify-between">
        {label && (
          <label className="block text-xs font-semibold uppercase tracking-wider text-muted">
            {label}
          </label>
        )}

        {/* Tab switcher */}
        <div className="flex items-center gap-1 p-0.5 rounded-lg bg-secondary/60 border border-border/60 ml-auto">
          <button
            type="button"
            onClick={() => setMode("write")}
            className={cn(
              "flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md transition-all cursor-pointer",
              mode === "write"
                ? "bg-card text-foreground shadow-xs font-semibold"
                : "text-muted hover:text-foreground"
            )}
          >
            <PenLine size={12} />
            <span>Write</span>
          </button>
          <button
            type="button"
            onClick={() => setMode("preview")}
            className={cn(
              "flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md transition-all cursor-pointer",
              mode === "preview"
                ? "bg-card text-foreground shadow-xs font-semibold"
                : "text-muted hover:text-foreground"
            )}
          >
            <Eye size={12} />
            <span>Preview</span>
          </button>
        </div>
      </div>

      {/* Editor Body */}
      <div className="rounded-xl border border-border bg-card overflow-hidden focus-within:ring-2 focus-within:ring-accent/30 focus-within:border-accent transition-all">
        {mode === "write" ? (
          <div>
            {/* Formatting Toolbar */}
            <div className="flex items-center flex-wrap gap-0.5 px-2 py-1.5 bg-secondary/40 border-b border-border/70 text-muted">
              <button
                type="button"
                onClick={() => insertFormat("**", "**", "bold text")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Bold (Ctrl+B)"
              >
                <Bold size={13} />
              </button>
              <button
                type="button"
                onClick={() => insertFormat("*", "*", "italic text")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Italic (Ctrl+I)"
              >
                <Italic size={13} />
              </button>
              <button
                type="button"
                onClick={() => insertFormat("~~", "~~", "strikethrough text")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Strikethrough"
              >
                <Strikethrough size={13} />
              </button>

              <span className="w-px h-4 bg-border/80 mx-1" />

              <button
                type="button"
                onClick={() => insertFormat("### ", "", "Heading")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Heading (H3)"
              >
                <Heading2 size={13} />
              </button>
              <button
                type="button"
                onClick={() => insertFormat("- [ ] ", "", "Checklist item")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Checklist item"
              >
                <CheckSquare size={13} />
              </button>
              <button
                type="button"
                onClick={() => insertFormat("- ", "", "List item")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Bullet list"
              >
                <List size={13} />
              </button>
              <button
                type="button"
                onClick={() => insertFormat("1. ", "", "List item")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Numbered list"
              >
                <ListOrdered size={13} />
              </button>

              <span className="w-px h-4 bg-border/80 mx-1" />

              <button
                type="button"
                onClick={() => insertFormat("`", "`", "code")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Inline code"
              >
                <Code size={13} />
              </button>
              <button
                type="button"
                onClick={() => insertFormat("[", "](https://example.com)", "link title")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Link"
              >
                <LinkIcon size={13} />
              </button>
              <button
                type="button"
                onClick={() => insertFormat("> ", "", "Quote")}
                className="p-1.5 rounded hover:bg-secondary hover:text-foreground transition-colors cursor-pointer"
                title="Blockquote"
              >
                <Quote size={13} />
              </button>
            </div>

            {/* Write Textarea */}
            <textarea
              ref={textareaRef}
              value={value}
              onChange={(e: ChangeEvent<HTMLTextAreaElement>) => onChange(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={placeholder}
              className={cn(
                "w-full p-3 text-xs sm:text-sm bg-transparent text-foreground placeholder:text-muted/60 focus:outline-none resize-y leading-relaxed font-sans",
                minHeight
              )}
            />

            {/* Footer with Character Counter */}
            <div className="flex items-center justify-between px-3 py-1.5 bg-secondary/20 border-t border-border/50 text-[10px] text-muted">
              <span>Markdown supported</span>
              <span>
                {value.length} {value.length === 1 ? "char" : "chars"}
              </span>
            </div>
          </div>
        ) : (
          /* Preview Mode */
          <div className="p-3.5">
            <MarkdownViewer
              content={value}
              placeholder="No description or notes written yet. Switch to 'Write' to add details."
              className={minHeight}
            />
          </div>
        )}
      </div>
    </div>
  );
};
