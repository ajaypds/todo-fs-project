import type { Label } from "../labelTypes";

type Props = { label: Label };

export const LabelBadge = ({ label }: Props) => {
  return (
    <div
      className="
        inline-flex items-center
        rounded-full
        px-2.5 py-1
        text-xs font-medium
        text-white
      "
      style={{
        backgroundColor: label.color,
      }}
    >
      {label.name}
    </div>
  );
};
