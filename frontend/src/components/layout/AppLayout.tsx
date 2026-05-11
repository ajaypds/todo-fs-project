import { type ReactNode } from "react";

type Props = {
  children: ReactNode;
};

export const AppLayout = ({ children }: Props) => {
  return (
    <div className="flex h-screen bg-gray-100">
      <aside className="w-64 bg-white border-r">
        <div className="p-4 text-xl font-bold">Todo App</div>

        <nav className="p-2 space-y-2">
          <div className="p-2 rounded hover:bg-gray-100 cursor-pointer">
            Inbox
          </div>

          <div className="p-2 rounded hover:bg-gray-100 cursor-pointer">
            Today
          </div>

          <div className="p-2 rounded hover:bg-gray-100 cursor-pointer">
            Upcoming
          </div>
        </nav>
      </aside>

      <main className="flex-1 overflow-auto p-6">{children}</main>
    </div>
  );
};
