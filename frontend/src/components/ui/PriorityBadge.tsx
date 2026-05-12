type Props = {
  priority: number;
};

const priorityMap: Record<number, { label: string; className: string }> = {
  1: {
    label: "P1",
    className: "bg-red-100 text-red-700",
  },

  2: {
    label: "P2",
    className: "bg-orange-100 text-orange-700",
  },

  3: {
    label: "P3",
    className: "bg-blue-100 text-blue-700",
  },

  4: {
    label: "P4",
    className: "bg-gray-100 text-gray-700",
  },
};

export const PriorityBadge = ({ priority }: Props) => {
  const config = priorityMap[priority] ?? priorityMap[4];

  return (
    <span
      className={`
        text-xs px-2 py-1 rounded-full font-medium
        ${config.className}
      `}
    >
      {config.label}
    </span>
  );
};
