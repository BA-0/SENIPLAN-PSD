import * as React from "react";
import { cn } from "@/lib/utils";

const Table = React.forwardRef<HTMLTableElement, React.HTMLAttributes<HTMLTableElement>>(({ className, ...props }, ref) => (
  <div className="w-full overflow-x-auto rounded-lg border border-border scrollbar-thin">
    <table ref={ref} className={cn("w-full caption-bottom text-sm border-collapse", className)} {...props} />
  </div>
));
Table.displayName = "Table";

const TableHeader = React.forwardRef<HTMLTableSectionElement, React.HTMLAttributes<HTMLTableSectionElement>>(
  ({ className, ...props }, ref) => <thead ref={ref} className={cn("bg-muted/60 sticky top-0 z-[1]", className)} {...props} />
);
TableHeader.displayName = "TableHeader";

const TableBody = React.forwardRef<HTMLTableSectionElement, React.HTMLAttributes<HTMLTableSectionElement>>(
  ({ className, ...props }, ref) => <tbody ref={ref} className={cn("", className)} {...props} />
);
TableBody.displayName = "TableBody";

/**
 * `total` : ligne de totaux (fond vert, semi-gras). `band` : bandeau de regroupement du canevas
 * (axe, effet, niveau, bloc), en petites capitales sur fond gris, comme dans les documents exportes.
 */
const TableRow = React.forwardRef<HTMLTableRowElement, React.HTMLAttributes<HTMLTableRowElement> & { total?: boolean; band?: boolean }>(
  ({ className, total, band, ...props }, ref) => (
    <tr
      ref={ref}
      className={cn(
        "border-b border-border/60 last:border-0 transition-colors duration-150",
        !total && !band && "hover:bg-muted/50",
        total && "font-semibold bg-primary-50 dark:bg-primary-500/15",
        band && "bg-muted/60 text-[12px] font-semibold uppercase tracking-wide text-foreground/80",
        className
      )}
      {...props}
    />
  )
);
TableRow.displayName = "TableRow";

const TableHead = React.forwardRef<HTMLTableCellElement, React.ThHTMLAttributes<HTMLTableCellElement>>(({ className, ...props }, ref) => (
  <th
    ref={ref}
    className={cn(
      "h-11 px-3 text-left align-middle text-[12px] font-semibold uppercase tracking-wide text-muted-foreground whitespace-nowrap",
      className
    )}
    {...props}
  />
));
TableHead.displayName = "TableHead";

/** `label` : cellule d'intitule de ligne du canevas (fond gris clair, texte en medium, calee en haut). */
const TableCell = React.forwardRef<HTMLTableCellElement, React.TdHTMLAttributes<HTMLTableCellElement> & { label?: boolean }>(
  ({ className, label, ...props }, ref) => (
    <td
      ref={ref}
      className={cn(
        "px-3 py-2 min-h-[48px]",
        label ? "bg-muted/30 align-top text-[13px] font-medium leading-snug text-foreground/90" : "align-middle",
        className
      )}
      {...props}
    />
  )
);
TableCell.displayName = "TableCell";

/** Ligne unique d'un tableau sans contenu : le tableau garde ses colonnes, le message prend toute la largeur. */
function TableEmptyRow({ colSpan, children }: { colSpan: number; children: React.ReactNode }) {
  return (
    <TableRow className="hover:bg-transparent">
      <TableCell colSpan={colSpan} className="py-8 text-center text-[13px] text-muted-foreground">
        {children}
      </TableCell>
    </TableRow>
  );
}

export { Table, TableHeader, TableBody, TableRow, TableHead, TableCell, TableEmptyRow };
