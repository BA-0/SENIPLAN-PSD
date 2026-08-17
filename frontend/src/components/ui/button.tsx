import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { Loader2 } from "lucide-react";

import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium transition-colors duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/40 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 disabled:cursor-not-allowed",
  {
    variants: {
      variant: {
        primary: "bg-primary-500 text-white hover:bg-primary-600 active:bg-primary-700",
        secondary:
          "bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 hover:border-slate-300 active:bg-slate-100",
        ghost: "bg-transparent text-slate-600 hover:bg-slate-100 active:bg-slate-200",
        destructive: "bg-accent-500 text-white hover:bg-accent-600 active:bg-accent-700",
        destructiveGhost: "bg-transparent text-accent-500 hover:bg-accent-50 active:bg-accent-100",
        link: "bg-transparent text-primary-500 hover:underline hover:text-primary-600 p-0 h-auto",
        submit:
          "bg-primary-500 text-white hover:bg-primary-600 active:bg-primary-700 shadow-md shadow-primary-500/20",
      },
      size: {
        default: "h-10 px-4",
        sm: "h-9 px-3 text-[13px]",
        lg: "h-11 px-6 text-[15px]",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "default",
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  loading?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, loading = false, disabled, children, ...props }, ref) => {
    if (asChild) {
      // Slot requires exactly one child element to clone props onto — no loading spinner sibling here.
      return (
        <Slot className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props}>
          {children}
        </Slot>
      );
    }
    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        disabled={disabled || loading}
        {...props}
      >
        {loading && <Loader2 className="h-4 w-4 animate-spin" />}
        {children}
      </button>
    );
  }
);
Button.displayName = "Button";

export { Button, buttonVariants };
