import { Activity, CheckCircle2, RotateCcw, Send, LogIn, ShieldCheck, ShieldX, Undo2, Trash2, Pencil, X } from "lucide-react";
import { timeAgo } from "@/lib/utils";
import type { ActivityEntryDto } from "@/types/api";

/** Designation d'une section par son intitule : les codes (S01, S09B...) restent internes. */
function laSection(a: ActivityEntryDto): string {
  return a.sectionTitle ? `la section « ${a.sectionTitle} »` : "une section";
}

const ACTION_CONFIG: Record<string, { label: (a: ActivityEntryDto) => string; icon: React.ElementType; color: string }> = {
  SAVE_DRAFT: {
    label: (a) => `${a.groupName} a enregistré un brouillon de ${laSection(a)}`,
    icon: Activity,
    color: "text-blue-500 dark:text-blue-400",
  },
  SUBMIT: {
    label: (a) => `${a.groupName} a soumis ${laSection(a)}`,
    icon: Send,
    color: "text-violet-500 dark:text-violet-400",
  },
  SUBMIT_ALL: {
    label: (a) => `${a.groupName} a soumis la totalité de ses sections`,
    icon: CheckCircle2,
    color: "text-emerald-600 dark:text-emerald-400",
  },
  VALIDATE: {
    label: (a) => `L'administrateur a validé ${laSection(a)} pour ${a.groupName}`,
    icon: CheckCircle2,
    color: "text-emerald-600 dark:text-emerald-400",
  },
  DG_APPROVE: {
    label: (a) => `La Direction Générale a approuvé ${laSection(a)} pour ${a.groupName}`,
    icon: ShieldCheck,
    color: "text-emerald-600 dark:text-emerald-400",
  },
  DG_REJECT: {
    label: (a) => `La Direction Générale a refusé ${laSection(a)} (${a.groupName}) — section renvoyée en révision`,
    icon: ShieldX,
    color: "text-red-500 dark:text-red-400",
  },
  DG_APPROVAL_REVOKED: {
    label: (a) =>
      `${a.sectionTitle ? `Section « ${a.sectionTitle} »` : "Section"} modifiée après approbation : l'accord de la Direction Générale est à redemander (${a.groupName})`,
    icon: ShieldX,
    color: "text-amber-500 dark:text-amber-400",
  },
  REQUEST_REVISION: {
    label: (a) => `L'administrateur a renvoyé ${laSection(a)} pour révision (${a.groupName})`,
    icon: RotateCcw,
    color: "text-amber-500 dark:text-amber-400",
  },
  RETURN_TO_GROUP: {
    label: (a) => `L'administrateur a redonné la main sur ${laSection(a)} à ${a.groupName}`,
    icon: Undo2,
    color: "text-amber-500 dark:text-amber-400",
  },
  RESET: {
    label: (a) => `L'administrateur a réinitialisé ${laSection(a)} pour ${a.groupName}`,
    icon: Trash2,
    color: "text-red-500 dark:text-red-400",
  },
  PURGE_DATA: {
    label: (a) => `L'administrateur a effacé toutes les saisies de ${a.groupName}`,
    icon: Trash2,
    color: "text-red-500 dark:text-red-400",
  },
  ADMIN_EDIT: {
    label: (a) => `L'administrateur a modifié ${laSection(a)} pour ${a.groupName}`,
    icon: Pencil,
    color: "text-blue-500 dark:text-blue-400",
  },
  LOGIN: {
    label: (a) => `${a.userFullName ?? a.groupName} s'est connecté(e)`,
    icon: LogIn,
    color: "text-muted-foreground",
  },
};

/** `onDelete` : fourni pour l'admin seul, affiche au survol une croix qui retire l'entrée du fil. */
export function ActivityFeed({ entries, onDelete }: { entries: ActivityEntryDto[]; onDelete?: (id: number) => void }) {
  if (entries.length === 0) {
    return <p className="text-[13px] text-muted-foreground italic py-6 text-center">Aucune activité récente</p>;
  }

  return (
    <ul className="space-y-3" aria-live="polite" aria-atomic="false">
      {entries.map((entry) => {
        const config = ACTION_CONFIG[entry.action] ?? ACTION_CONFIG.SAVE_DRAFT;
        const Icon = config.icon;
        return (
          <li key={entry.id} className="group flex items-start gap-3">
            <Icon aria-hidden="true" className={`h-4 w-4 mt-0.5 shrink-0 ${config.color}`} />
            <div className="min-w-0 flex-1">
              <p className="text-[13px] text-foreground">{config.label(entry)}</p>
              <p className="text-[12px] text-muted-foreground">{timeAgo(entry.timestamp)}</p>
            </div>
            {onDelete && (
              <button
                type="button"
                onClick={() => onDelete(entry.id)}
                title="Retirer cette activité"
                aria-label="Retirer cette activité"
                className="mt-0.5 shrink-0 rounded p-0.5 text-muted-foreground opacity-0 transition-opacity hover:bg-muted hover:text-red-600 focus-visible:opacity-100 group-hover:opacity-100"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </li>
        );
      })}
    </ul>
  );
}
