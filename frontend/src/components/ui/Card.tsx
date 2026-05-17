import type { HTMLAttributes } from "react";
import { forwardRef } from "react";

import { cn } from "../../lib/cn";

export const Card = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn(
          `
          bg-card
          border border-border
          rounded-2xl
          shadow-soft
        `,

          className,
        )}
        {...props}
      />
    );
  },
);

Card.displayName = "Card";
