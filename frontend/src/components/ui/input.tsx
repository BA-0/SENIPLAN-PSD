import * as React from "react";
import { cn } from "@/lib/utils";

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: boolean;
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, error, ...props }, ref) => {
    return (
      <input
        type={type}
        ref={ref}
        className={cn(
          "flex h-10 w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-800 placeholder:text-slate-400 transition-colors duration-150",
          "focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500",
          "disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500",
          error ? "border-accent-500" : "border-slate-200",
          className
        )}
        {...props}
      />
    );
  }
);
Input.displayName = "Input";

export { Input };
