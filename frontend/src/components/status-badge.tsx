import { Badge } from "@/components/ui/badge";
import type { SectionStatus } from "@/types/common";

const STATUS_CONFIG: Record<SectionStatus, { label: string; variant: string }> = {
  NOT_STARTED: { label: "Non commencé", variant: "notStarted" },
  IN_PROGRESS: { label: "En cours", variant: "inProgress" },
  SUBMITTED: { label: "Soumis", variant: "submitted" },
  VALIDATED: { label: "Validé", variant: "validated" },
  REVISION_REQUESTED: { label: "À réviser", variant: "revision" },
};

export function StatusBadge({ status }: { status: SectionStatus }) {
  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.NOT_STARTED;
  return <Badge variant={config.variant as never}>{config.label}</Badge>;
}

const CRITICALITY_CONFIG: Record<string, { label: string; variant: string }> = {
  ELEVEE: { label: "Élevée — action prioritaire", variant: "criticalHigh" },
  MOYENNE: { label: "Moyenne — à surveiller", variant: "criticalMedium" },
  FAIBLE: { label: "Faible — sous contrôle", variant: "criticalLow" },
};

export function CriticalityBadge({ label }: { label: string }) {
  const config = CRITICALITY_CONFIG[label] ?? CRITICALITY_CONFIG.FAIBLE;
  return <Badge variant={config.variant as never}>{config.label}</Badge>;
}
