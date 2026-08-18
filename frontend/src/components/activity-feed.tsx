import { Activity, CheckCircle2, RotateCcw, Send, LogIn } from "lucide-react";
import { timeAgo } from "@/lib/utils";
import type { ActivityEntryDto } from "@/types/api";

const ACTION_CONFIG: Record<string, { label: (a: ActivityEntryDto) => string; icon: React.ElementType; color: string }> = {
  SAVE_DRAFT: {
    label: (a) => `${a.groupName} a enregistré un brouillon sur ${a.sectionCode} — ${a.sectionTitle}`,
    icon: Activity,
    color: "text-blue-500 dark:text-blue-400",
  },
  SUBMIT: {
    label: (a) => `${a.groupName} a soumis la Section ${a.sectionCode?.replace("S", "")} — ${a.sectionTitle}`,
    icon: Send,
    color: "text-violet-500 dark:text-violet-400",
  },
  VALIDATE: {
    label: (a) => `L'administrateur a validé ${a.sectionCode} pour ${a.groupName}`,
    icon: CheckCircle2,
    color: "text-emerald-600 dark:text-emerald-400",
  },
  REQUEST_REVISION: {
    label: (a) => `L'administrateur a renvoyé ${a.sectionCode} pour révision (${a.groupName})`,
    icon: RotateCcw,
    color: "text-amber-500 dark:text-amber-400",
  },
  LOGIN: {
    label: (a) => `${a.userFullName ?? a.groupName} s'est connecté(e)`,
    icon: LogIn,
    color: "text-muted-foreground",
  },
};

export function ActivityFeed({ entries }: { entries: ActivityEntryDto[] }) {
  if (entries.length === 0) {
    return <p className="text-[13px] text-muted-foreground italic py-6 text-center">Aucune activité récente</p>;
  }

  return (
    <ul className="space-y-3" aria-live="polite" aria-atomic="false">
      {entries.map((entry) => {
        const config = ACTION_CONFIG[entry.action] ?? ACTION_CONFIG.SAVE_DRAFT;
        const Icon = config.icon;
        return (
          <li key={entry.id} className="flex items-start gap-3">
            <Icon aria-hidden="true" className={`h-4 w-4 mt-0.5 shrink-0 ${config.color}`} />
            <div className="min-w-0">
              <p className="text-[13px] text-foreground">{config.label(entry)}</p>
              <p className="text-[12px] text-muted-foreground">{timeAgo(entry.timestamp)}</p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
