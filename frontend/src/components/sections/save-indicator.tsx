import { CheckCircle2, Loader2, AlertCircle } from "lucide-react";
import type { SaveStatus } from "@/hooks/use-section-autosave";

export function SaveIndicator({ status, savedAt }: { status: SaveStatus; savedAt: Date | null }) {
  if (status === "saving") {
    return (
      <span className="flex items-center gap-1.5 text-[13px] text-muted-foreground">
        <Loader2 className="h-3.5 w-3.5 animate-spin" /> Enregistrement…
      </span>
    );
  }
  if (status === "error") {
    return (
      <span className="flex items-center gap-1.5 text-[13px] text-accent-700 dark:text-accent-300">
        <AlertCircle className="h-3.5 w-3.5" /> Échec de l&apos;enregistrement
      </span>
    );
  }
  if (savedAt) {
    return (
      <span className="flex items-center gap-1.5 text-[13px] text-muted-foreground">
        <CheckCircle2 className="h-3.5 w-3.5 text-primary-500" />
        Enregistré à {savedAt.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}
      </span>
    );
  }
  return null;
}
