import { type ReactNode } from "react";

type Props = {
  children: ReactNode;
};

export const AppLayout = ({ children }: Props) => {
  return (
    <div className="flex flex-col md:flex-row h-screen bg-gray-100">
      <aside className="w-full md:w-64 bg-white border-r">
        <div className="p-4 text-xl font-bold">Todo App</div>

        <nav className="p-2 space-y-1">
          <button className="w-full text-left p-3 rounded-lg hover:bg-gray-100 transition">
            Inbox
          </button>

          <button className="w-full text-left p-3 rounded-lg hover:bg-gray-100 transition">
            Today
          </button>

          <button className="w-full text-left p-3 rounded-lg hover:bg-gray-100 transition">
            Upcoming
          </button>
        </nav>
      </aside>

      <main className="flex-1 overflow-auto p-6">{children}</main>
    </div>
  );
};
