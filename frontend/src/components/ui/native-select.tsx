import * as React from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";

export interface NativeSelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  cellStyle?: boolean;
}

const NativeSelect = React.forwardRef<HTMLSelectElement, NativeSelectProps>(
  ({ className, cellStyle, children, ...props }, ref) => {
    return (
      <div className="relative inline-block w-full">
        <select
          ref={ref}
          className={cn(
            "h-10 w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3 pr-8 text-sm text-slate-800 transition-colors duration-150",
            "focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500",
            "disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500",
            cellStyle && "h-9 border-transparent bg-transparent hover:bg-slate-50 focus:bg-white",
            className
          )}
          {...props}
        >
          {children}
        </select>
        <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
      </div>
    );
  }
);
NativeSelect.displayName = "NativeSelect";

export { NativeSelect };
