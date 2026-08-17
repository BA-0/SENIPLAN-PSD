import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium gap-1 whitespace-nowrap",
  {
    variants: {
      variant: {
        default: "bg-slate-100 text-slate-600",
        notStarted: "bg-slate-100 text-slate-500",
        inProgress: "bg-blue-50 text-blue-700",
        submitted: "bg-primary-50 text-primary-700",
        validated: "bg-primary-500 text-white",
        revision: "bg-orange-50 text-orange-700",
        criticalHigh: "bg-accent-100 text-accent-700",
        criticalMedium: "bg-amber-100 text-amber-700",
        criticalLow: "bg-primary-100 text-primary-700",
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
