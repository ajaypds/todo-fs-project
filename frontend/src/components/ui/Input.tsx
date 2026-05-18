import type { InputHTMLAttributes } from "react";
import { cn } from "../../lib/cn";

export const Input = ({
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement>) => {
  return (
    <input
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

          placeholder:text-muted
        `,

        className,
      )}
      {...props}
    />
  );
};
