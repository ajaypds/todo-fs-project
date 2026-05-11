type Props = {
  title: string;

  description: string;
};

export const EmptyState = ({ title, description }: Props) => {
  return (
    <div className="bg-white rounded-lg border p-12 text-center">
      <h2 className="text-xl font-semibold mb-2">{title}</h2>

      <p className="text-gray-500">{description}</p>
    </div>
  );
};
