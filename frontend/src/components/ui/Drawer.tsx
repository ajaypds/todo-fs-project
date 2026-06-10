import { type ReactNode } from "react";
import { X } from "lucide-react";

type Props = {
  open: boolean;

  onClose: () => void;

  title: string;

  children: ReactNode;
};

const Drawer = ({ open, onClose, title, children }: Props) => {
  if (!open) {
    return null;
  }

  return (
    <>
      <div
        onClick={onClose}
        className="
          fixed inset-0
          bg-black/40
          z-40
        "
      />

      <div
        className="
          fixed
          top-0
          right-0
          h-full
          w-full
          sm:w-125
          bg-card
          border-l
          border-border
          z-50
          p-6
          overflow-y-auto
          shadow-2xl
        "
      >
        <div
          className="
            flex
            items-center
            justify-between
            mb-6
          "
        >
          <h2
            className="
              text-lg
              font-semibold
            "
          >
            {title}
          </h2>

          <button onClick={onClose} className="cursor-pointer">
            <X size={20} />
          </button>
        </div>

        {children}
      </div>
    </>
  );
};

export default Drawer;
