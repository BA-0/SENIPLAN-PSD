import * as React from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";

export interface NativeSelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  cellStyle?: boolean;
}

const NativeSelect = React.forwardRef<HTMLSelectElement, NativeSelectProps>(
  ({ className, cellStyle, children, disabled, value, ...props }, ref) => {
    // Vue lecture (disabled = readOnly dans tous les formulaires de section) :
    // affiche le libelle de l'option selectionnee comme une simple etiquette,
    // pas un <select> grise avec chevron qui donne l'impression d'un formulaire vide.
    if (disabled) {
      const options = React.Children.toArray(children) as React.ReactElement<
        React.OptionHTMLAttributes<HTMLOptionElement>
      >[];
      const match = options.find((opt) => String(opt.props.value ?? "") === String(value ?? ""));
      const label = match?.props.children;
      const isEmpty = !label || (typeof label === "string" && ["", "—"].includes(label.trim()));

      return (
        <span
          className={cn(
            "inline-flex items-center rounded-full px-2.5 py-1 text-[12.5px] leading-none",
            isEmpty ? "italic text-muted-foreground/70" : "bg-muted/60 font-medium text-foreground/90",
            className
          )}
        >
          {isEmpty ? "—" : label}
        </span>
      );
    }

    return (
      <div className="relative inline-block w-full">
        <select
          ref={ref}
          disabled={disabled}
          value={value}
          className={cn(
            "h-10 w-full appearance-none rounded-lg border border-border bg-card pl-3 pr-8 text-sm text-foreground transition-colors duration-150",
            "focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500",
            cellStyle && "h-9 border-transparent bg-transparent hover:bg-muted/50 focus:bg-card",
            className
          )}
          {...props}
        >
          {children}
        </select>
        <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
      </div>
    );
  }
);
NativeSelect.displayName = "NativeSelect";

export { NativeSelect };
