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
        `,

        variant === "primary" &&
          `
            bg-primary
            text-white
            hover:opacity-90
            shadow-soft
          `,

        variant === "secondary" &&
          `
            bg-secondary
            text-foreground
            hover:bg-gray-200
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
