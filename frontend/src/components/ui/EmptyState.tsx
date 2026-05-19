import { Inbox } from "lucide-react";

type Props = {
  title: string;

  description: string;
};

export const EmptyState = ({ title, description }: Props) => {
  return (
    <div className="border border-dashed border-border rounded-2xl p-16 text-center bg-card">
      <div className="w-14 h-14 rounded-2xl bg-secondary flex items-center justify-center mx-auto mb-5">
        <Inbox size={26} />
      </div>
      <h2 className="text-xl font-semibold mb-2">{title}</h2>
      <p className="text-muted max-w-sm mx-auto">{description}</p>
    </div>
  );
};
