import type { SelectHTMLAttributes } from "react";

import { cn } from "../../lib/cn";

export const Select = ({
  className,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement>) => {
  return (
    <select
      className={cn(
        `
          w-full
          rounded-xl
          border border-border
          bg-card
          px-4 py-3
          text-sm
          outline-none
          transition-all
          duration-200

          focus:ring-2
          focus:ring-accent/20
          focus:border-accent

          text-foreground
        `,

        className,
      )}
      {...props}
    >
      {children}
    </select>
  );
};
