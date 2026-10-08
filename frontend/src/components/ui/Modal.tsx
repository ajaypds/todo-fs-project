import type { ReactNode } from "react";
import { useEffect } from "react";

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
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 sm:p-6 overflow-y-auto"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={`bg-background border border-border rounded-2xl shadow-2xl w-full ${maxWidth} my-auto p-5 sm:p-6 max-h-[90vh] flex flex-col ${className}`}
      >
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-semibold">{title}</h2>

          <button
            onClick={onClose}
            className="text-gray-500 hover:text-black hover:cursor-pointer"
          >
            ✕
          </button>
        </div>

        {children}
      </div>
    </div>
  );
};

export default Modal;
