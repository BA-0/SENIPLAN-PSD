import * as React from "react";
import { cn } from "@/lib/utils";

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: boolean;
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, error, readOnly, value, ...props }, ref) => {
    if (readOnly) {
      const text = typeof value === "string" ? value.trim() : "";
      return (
        <p className="w-full py-2 text-[13.5px] leading-snug text-foreground/90 break-words">
          {text || <span className="italic text-muted-foreground/70">—</span>}
        </p>
      );
    }

    return (
      <input
        type={type}
        ref={ref}
        value={value}
        className={cn(
          "flex h-10 w-full rounded-lg border bg-card px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground/70 transition-colors duration-150",
          "focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500",
          "disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground",
          error ? "border-accent-500" : "border-border",
          className
        )}
        {...props}
      />
    );
  }
);
Input.displayName = "Input";

export { Input };
