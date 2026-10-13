import type { ReactNode } from "react";
import { useEffect } from "react";
import { X } from "lucide-react";
import { cn } from "../../lib/cn";

type Props = {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  maxWidth?: string;
  className?: string;
};

export const Modal = ({
  open,
  title,
  onClose,
  children,
  maxWidth = "max-w-lg",
  className = "",
}: Props) => {
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleEscape);

    return () => {
      window.removeEventListener("keydown", handleEscape);
    };
  }, [onClose]);

  if (!open) {
    return null;
  }

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-xs p-0 sm:p-6 overflow-y-auto"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={cn(
          "bg-background border border-border shadow-2xl w-full my-0 sm:my-auto flex flex-col",
          "rounded-t-3xl sm:rounded-2xl rounded-b-none sm:rounded-b-2xl",
          "max-h-[92vh] sm:max-h-[85vh] p-5 sm:p-6",
          maxWidth,
          className
        )}
      >
        {/* Mobile handle indicator */}
        <div className="flex justify-center pb-3 -mt-1 sm:hidden">
          <div className="w-12 h-1.5 rounded-full bg-muted/40" />
        </div>

        <div className="flex items-center justify-between mb-5 sm:mb-6 shrink-0">
          <h2 className="text-lg sm:text-xl font-semibold tracking-tight">{title}</h2>

          <button
            type="button"
            onClick={onClose}
            className="p-1.5 -mr-1.5 rounded-full text-muted hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto">
          {children}
        </div>
      </div>
    </div>
  );
};

export default Modal;

