import type { ButtonHTMLAttributes } from "react";

import { cn } from "../../lib/cn";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost";
};

export const Button = ({ className, variant = "primary", ...props }: Props) => {
  return (
    <button
      className={cn(
        `
          inline-flex items-center
          justify-center
          rounded-xl
          px-4 py-2.5
          text-sm font-medium
          transition-all
          duration-200
          ease-smooth
          focus:outline-none
          hover:cursor-pointer
        `,

        variant === "primary" &&
          `
            bg-primary
            text-background
            hover:opacity-80
            shadow-soft
          `,

        variant === "secondary" &&
          `
            bg-secondary
            text-foreground
            hover:opacity-80
            shadow-soft
          `,

        variant === "ghost" &&
          `
            hover:bg-secondary
          `,

        className,
      )}
      {...props}
    />
  );
};
