import React, { useMemo } from "react";
import { cn } from "../../lib/cn";
import { ExternalLink } from "lucide-react";

type Props = {
  content: string;
  className?: string;
  placeholder?: string;
};

// Helper to parse inline markdown: bold, italic, strikethrough, code, links
const renderInline = (text: string): React.ReactNode => {
  if (!text) return null;

  // Regex patterns for inline syntax
  // 1: Code `code`
  // 2: Bold **bold**
  // 3: Italic *italic*
  // 4: Strikethrough ~~strike~~
  // 5: Links [text](url)
  const regex = /(`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*|~~[^~]+~~|\[[^\]]+\]\([^)]+\))/g;
  const parts = text.split(regex);

  return parts.map((part, index) => {
    if (!part) return null;

    // Inline Code: `code`
    if (part.startsWith("`") && part.endsWith("`") && part.length >= 2) {
      return (
        <code
          key={index}
          className="px-1.5 py-0.5 rounded bg-secondary/90 text-[11px] sm:text-xs font-mono text-accent border border-border/50"
        >
          {part.slice(1, -1)}
        </code>
      );
    }

    // Bold: **text**
    if (part.startsWith("**") && part.endsWith("**") && part.length >= 4) {
      return (
        <strong key={index} className="font-semibold text-foreground">
          {renderInline(part.slice(2, -2))}
        </strong>
      );
    }

    // Italic: *text*
    if (part.startsWith("*") && part.endsWith("*") && part.length >= 2) {
      return (
        <em key={index} className="italic text-foreground/90">
          {renderInline(part.slice(1, -1))}
        </em>
      );
    }

    // Strikethrough: ~~text~~
    if (part.startsWith("~~") && part.endsWith("~~") && part.length >= 4) {
      return (
        <span key={index} className="line-through text-muted">
          {renderInline(part.slice(2, -2))}
        </span>
      );
    }

    // Link: [text](url)
    const linkMatch = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
    if (linkMatch) {
      const [, label, url] = linkMatch;
      return (
        <a
          key={index}
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-accent hover:underline inline-flex items-center gap-0.5 font-medium cursor-pointer"
        >
          <span>{label}</span>
          <ExternalLink size={10} className="inline opacity-70 shrink-0" />
        </a>
      );
    }

    return <React.Fragment key={index}>{part}</React.Fragment>;
  });
};

type Block =
  | { type: "heading"; level: number; text: string }
  | { type: "code"; language?: string; code: string }
  | { type: "blockquote"; text: string }
  | { type: "hr" }
  | { type: "tasklist"; items: { checked: boolean; text: string }[] }
  | { type: "ul"; items: string[] }
  | { type: "ol"; items: string[] }
  | { type: "table"; headers: string[]; rows: string[][] }
  | { type: "paragraph"; text: string };

export const MarkdownViewer = ({
  content,
  className,
  placeholder = "No notes or description provided yet.",
}: Props) => {
  const blocks = useMemo<Block[]>(() => {
    if (!content || !content.trim()) return [];

    const lines = content.replace(/\r\n/g, "\n").split("\n");
    const result: Block[] = [];
    let i = 0;

    while (i < lines.length) {
      const line = lines[i];

      // 1. Fenced Code Block: ```lang
      if (line.trim().startsWith("```")) {
        const lang = line.trim().slice(3).trim();
        const codeLines: string[] = [];
        i++;
        while (i < lines.length && !lines[i].trim().startsWith("```")) {
          codeLines.push(lines[i]);
          i++;
        }
        if (i < lines.length) i++; // consume closing ```
        result.push({
          type: "code",
          language: lang,
          code: codeLines.join("\n"),
        });
        continue;
      }

      // 2. Horizontal Rule: --- or ***
      if (/^(?:---|\*\*\*|___)\s*$/.test(line.trim())) {
        result.push({ type: "hr" });
        i++;
        continue;
      }

      // 3. Headings: # H1, ## H2, ### H3, etc.
      const headingMatch = line.match(/^(#{1,6})\s+(.*)$/);
      if (headingMatch) {
        result.push({
          type: "heading",
          level: headingMatch[1].length,
          text: headingMatch[2],
        });
        i++;
        continue;
      }

      // 4. Blockquote: > text
      if (line.trim().startsWith(">")) {
        const quoteLines: string[] = [];
        while (i < lines.length && lines[i].trim().startsWith(">")) {
          quoteLines.push(lines[i].replace(/^>\s?/, ""));
          i++;
        }
        result.push({
          type: "blockquote",
          text: quoteLines.join("\n"),
        });
        continue;
      }

      // 5. Task List: - [ ] or - [x]
      if (/^\s*[-*]\s+\[([ xX])\]\s+/.test(line)) {
        const taskItems: { checked: boolean; text: string }[] = [];
        while (i < lines.length) {
          const m = lines[i].match(/^\s*[-*]\s+\[([ xX])\]\s+(.*)$/);
          if (!m) break;
          taskItems.push({
            checked: m[1].toLowerCase() === "x",
            text: m[2],
          });
          i++;
        }
        result.push({ type: "tasklist", items: taskItems });
        continue;
      }

      // 6. Unordered List: - item or * item
      if (/^\s*[-*]\s+/.test(line)) {
        const listItems: string[] = [];
        while (i < lines.length) {
          const m = lines[i].match(/^\s*[-*]\s+(.*)$/);
          if (!m || /^\s*[-*]\s+\[([ xX])\]/.test(lines[i])) break;
          listItems.push(m[1]);
          i++;
        }
        result.push({ type: "ul", items: listItems });
        continue;
      }

      // 7. Ordered List: 1. item
      if (/^\s*\d+\.\s+/.test(line)) {
        const listItems: string[] = [];
        while (i < lines.length) {
          const m = lines[i].match(/^\s*\d+\.\s+(.*)$/);
          if (!m) break;
          listItems.push(m[1]);
          i++;
        }
        result.push({ type: "ol", items: listItems });
        continue;
      }

      // 8. Markdown Table: | Col 1 | Col 2 |
      if (line.trim().startsWith("|") && line.trim().endsWith("|") && i + 1 < lines.length && lines[i + 1].includes("---")) {
        const parseRow = (r: string) =>
          r
            .trim()
            .slice(1, -1)
            .split("|")
            .map((cell) => cell.trim());

        const headers = parseRow(line);
        i += 2; // skip header and separator row
        const rows: string[][] = [];
        while (i < lines.length && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
          rows.push(parseRow(lines[i]));
          i++;
        }
        result.push({ type: "table", headers, rows });
        continue;
      }

      // 9. Blank line
      if (!line.trim()) {
        i++;
        continue;
      }

      // 10. Paragraph
      const paraLines: string[] = [];
      while (
        i < lines.length &&
        lines[i].trim() &&
        !lines[i].trim().startsWith("```") &&
        !lines[i].trim().startsWith("#") &&
        !lines[i].trim().startsWith(">") &&
        !/^\s*[-*]\s+/.test(lines[i]) &&
        !/^\s*\d+\.\s+/.test(lines[i]) &&
        !/^(?:---|\*\*\*|___)\s*$/.test(lines[i].trim())
      ) {
        paraLines.push(lines[i]);
        i++;
      }
      result.push({ type: "paragraph", text: paraLines.join(" ") });
    }

    return result;
  }, [content]);

  if (!content || !content.trim()) {
    return (
      <div className={cn("text-xs text-muted/70 italic py-3", className)}>
        {placeholder}
      </div>
    );
  }

  return (
    <div
      className={cn(
        "space-y-2.5 text-xs sm:text-sm text-foreground/90 leading-relaxed break-words",
        className
      )}
    >
      {blocks.map((block, idx) => {
        switch (block.type) {
          case "heading": {
            if (block.level === 1) {
              return (
                <h1
                  key={idx}
                  className="text-base sm:text-lg font-bold text-foreground border-b border-border/50 pb-1 mt-3 first:mt-0"
                >
                  {renderInline(block.text)}
                </h1>
              );
            }
            if (block.level === 2) {
              return (
                <h2
                  key={idx}
                  className="text-sm sm:text-base font-semibold text-foreground mt-2.5 first:mt-0"
                >
                  {renderInline(block.text)}
                </h2>
              );
            }
            return (
              <h3
                key={idx}
                className="text-xs sm:text-sm font-semibold text-foreground/95 mt-2 first:mt-0"
              >
                {renderInline(block.text)}
              </h3>
            );
          }

          case "paragraph":
            return (
              <p key={idx} className="leading-relaxed">
                {renderInline(block.text)}
              </p>
            );

          case "blockquote":
            return (
              <blockquote
                key={idx}
                className="border-l-2 border-accent/70 pl-3 py-0.5 text-xs italic text-muted bg-secondary/30 rounded-r my-2"
              >
                {renderInline(block.text)}
              </blockquote>
            );

          case "code":
            return (
              <div key={idx} className="my-2 rounded-lg overflow-hidden border border-border/70 bg-secondary/80">
                {block.language && (
                  <div className="px-3 py-1 bg-secondary/90 text-[10px] uppercase font-mono tracking-wider text-muted border-b border-border/50">
                    {block.language}
                  </div>
                )}
                <pre className="p-3 text-xs font-mono overflow-x-auto text-foreground/90 leading-normal">
                  <code>{block.code}</code>
                </pre>
              </div>
            );

          case "hr":
            return <hr key={idx} className="border-border/60 my-3" />;

          case "tasklist":
            return (
              <ul key={idx} className="space-y-1.5 my-1.5 pl-0.5">
                {block.items.map((item, itemIdx) => (
                  <li key={itemIdx} className="flex items-start gap-2 text-xs">
                    <input
                      type="checkbox"
                      checked={item.checked}
                      readOnly
                      className="mt-0.5 w-3.5 h-3.5 rounded border-border text-accent focus:ring-0 shrink-0 cursor-default"
                    />
                    <span
                      className={cn(
                        "leading-tight",
                        item.checked && "line-through text-muted"
                      )}
                    >
                      {renderInline(item.text)}
                    </span>
                  </li>
                ))}
              </ul>
            );

          case "ul":
            return (
              <ul key={idx} className="list-disc list-inside space-y-1 my-1.5 pl-1 text-xs">
                {block.items.map((item, itemIdx) => (
                  <li key={itemIdx} className="leading-relaxed">
                    {renderInline(item)}
                  </li>
                ))}
              </ul>
            );

          case "ol":
            return (
              <ol key={idx} className="list-decimal list-inside space-y-1 my-1.5 pl-1 text-xs">
                {block.items.map((item, itemIdx) => (
                  <li key={itemIdx} className="leading-relaxed">
                    {renderInline(item)}
                  </li>
                ))}
              </ol>
            );

          case "table":
            return (
              <div key={idx} className="my-2 overflow-x-auto rounded-lg border border-border">
                <table className="w-full text-xs text-left border-collapse">
                  <thead className="bg-secondary/70 border-b border-border">
                    <tr>
                      {block.headers.map((h, hIdx) => (
                        <th key={hIdx} className="p-2 font-semibold text-foreground">
                          {renderInline(h)}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {block.rows.map((r, rIdx) => (
                      <tr key={rIdx} className="hover:bg-secondary/30">
                        {r.map((cell, cIdx) => (
                          <td key={cIdx} className="p-2 text-foreground/85">
                            {renderInline(cell)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );

          default:
            return null;
        }
      })}
    </div>
  );
};
