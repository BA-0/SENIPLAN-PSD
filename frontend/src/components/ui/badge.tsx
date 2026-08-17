import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium gap-1 whitespace-nowrap",
  {
    variants: {
      variant: {
        default: "bg-slate-100 text-slate-600 dark:bg-white/10 dark:text-slate-300",
        notStarted: "bg-slate-100 text-slate-500 dark:bg-white/10 dark:text-slate-300",
        inProgress: "bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300",
        submitted: "bg-primary-50 text-primary-700 dark:bg-primary-500/15 dark:text-primary-300",
        validated: "bg-primary-500 text-white dark:bg-primary-500/80",
        revision: "bg-orange-50 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300",
        criticalHigh: "bg-accent-100 text-accent-700 dark:bg-accent-500/15 dark:text-accent-300",
        criticalMedium: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300",
        criticalLow: "bg-primary-100 text-primary-700 dark:bg-primary-500/15 dark:text-primary-300",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}

export { Badge, badgeVariants };
