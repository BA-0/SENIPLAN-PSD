import * as React from "react";
import { cn } from "@/lib/utils";

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: boolean;
}

const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, error, readOnly, value, ...props }, ref) => {
    if (readOnly) {
      const text = typeof value === "string" ? value.trim() : "";
      return (
        <p className="w-full whitespace-pre-wrap break-words py-1 text-[13.5px] leading-relaxed text-foreground/90">
          {text || <span className="italic text-muted-foreground/70">—</span>}
        </p>
      );
    }

    return (
      <textarea
        ref={ref}
        value={value}
        className={cn(
          "flex min-h-[80px] w-full rounded-lg border bg-card px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground/70 transition-colors duration-150",
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
Textarea.displayName = "Textarea";

export { Textarea };
