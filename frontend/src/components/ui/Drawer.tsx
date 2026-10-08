import type { ReactNode } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useIsMobile } from "../../hooks/useIsMobile";
import { cn } from "../../lib/cn";

type Props = {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
};

const Drawer = ({ open, onClose, title, children }: Props) => {
  const isMobile = useIsMobile();

  return (
    <AnimatePresence>
      {open && (
        <>
          {/* Backdrop */}
          <motion.div
            onClick={onClose}
            className="fixed inset-0 bg-black/50 backdrop-blur-xs z-40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
          />

          {/* Drawer / Bottom Sheet */}
          <motion.div
            className={cn(
              "fixed z-50 bg-card shadow-2xl flex flex-col overflow-y-auto overscroll-contain",
              isMobile
                ? "inset-x-0 bottom-0 max-h-[92vh] w-full rounded-t-3xl border-t border-border px-5 pt-3 pb-8"
                : "top-0 right-0 h-full w-full sm:w-125 border-l border-border p-6"
            )}
            initial={isMobile ? { y: "100%" } : { x: "100%" }}
            animate={isMobile ? { y: 0 } : { x: 0 }}
            exit={isMobile ? { y: "100%" } : { x: "100%" }}
            transition={
              isMobile
                ? { type: "spring", damping: 30, stiffness: 320 }
                : { duration: 0.25, ease: "easeInOut" }
            }
            drag={isMobile ? "y" : false}
            dragConstraints={{ top: 0 }}
            dragElastic={{ top: 0.05, bottom: 0.5 }}
            onDragEnd={(_, info) => {
              if (isMobile && (info.offset.y > 100 || info.velocity.y > 400)) {
                onClose();
              }
            }}
          >
            {/* Mobile Drag Handle Bar */}
            {isMobile && (
              <div className="flex justify-center pb-3 pt-1 touch-none">
                <div className="w-12 h-1.5 rounded-full bg-muted/40 cursor-grab active:cursor-grabbing hover:bg-muted/60 transition-colors" />
              </div>
            )}

            {/* Header */}
            <div className="flex items-center justify-between mb-4 sm:mb-6 shrink-0">
              <h2 className="text-lg font-semibold tracking-tight">{title}</h2>

              <button
                type="button"
                onClick={onClose}
                className="p-2 -mr-2 rounded-full text-muted hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                aria-label="Close"
              >
                <X size={20} />
              </button>
            </div>

            {/* Scrollable Content */}
            <div className="flex-1 overflow-y-auto">
              {children}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default Drawer;

