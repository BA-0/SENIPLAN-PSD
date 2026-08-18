import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

export type KpiColor = "primary" | "blue" | "violet" | "emerald" | "amber" | "orange" | "rose" | "slate";

const COLOR_CLASSES: Record<KpiColor, string> = {
  primary: "bg-primary-500 text-white shadow-sm shadow-primary-500/30",
  blue: "bg-sky-500 text-white shadow-sm shadow-sky-500/30",
  violet: "bg-violet-500 text-white shadow-sm shadow-violet-500/30",
  emerald: "bg-emerald-500 text-white shadow-sm shadow-emerald-500/30",
  amber: "bg-amber-500 text-white shadow-sm shadow-amber-500/30",
  orange: "bg-orange-500 text-white shadow-sm shadow-orange-500/30",
  rose: "bg-accent-500 text-white shadow-sm shadow-accent-500/30",
  slate: "bg-slate-400 text-white dark:bg-slate-500",
};

const CARD_BG_CLASSES: Record<KpiColor, string> = {
  primary: "bg-primary-200/70 border-primary-300 dark:bg-primary-500/30 dark:border-primary-500/40",
  blue: "bg-sky-200/70 border-sky-300 dark:bg-sky-500/30 dark:border-sky-500/40",
  violet: "bg-violet-200/70 border-violet-300 dark:bg-violet-500/30 dark:border-violet-500/40",
  emerald: "bg-emerald-200/70 border-emerald-300 dark:bg-emerald-500/30 dark:border-emerald-500/40",
  amber: "bg-amber-200/70 border-amber-300 dark:bg-amber-500/30 dark:border-amber-500/40",
  orange: "bg-orange-200/70 border-orange-300 dark:bg-orange-500/30 dark:border-orange-500/40",
  rose: "bg-accent-200/70 border-accent-500/40 dark:bg-accent-500/30 dark:border-accent-500/40",
  slate: "bg-slate-300/60 border-slate-400 dark:bg-white/15 dark:border-white/25",
};

export function KpiCard({
  icon: Icon,
  label,
  value,
  subtitle,
  color = "primary",
}: {
  icon: React.ElementType;
  label: string;
  value: string | number;
  subtitle?: string;
  color?: KpiColor;
}) {
  return (
    <Card className={cn("transition-shadow hover:shadow-md", CARD_BG_CLASSES[color])}>
      <CardContent className="pt-5 flex items-start justify-between">
        <div>
          <p className="text-[13px] text-muted-foreground">{label}</p>
          <p className="text-[30px] font-bold text-foreground tabular-nums leading-tight mt-1">{value}</p>
          {subtitle && <p className="text-[12px] text-muted-foreground mt-0.5">{subtitle}</p>}
        </div>
        <div className={cn("h-10 w-10 rounded-lg flex items-center justify-center shrink-0", COLOR_CLASSES[color])}>
          <Icon className="h-5 w-5" strokeWidth={1.75} />
        </div>
      </CardContent>
    </Card>
  );
}
