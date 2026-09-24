import type { SubmissionSummaryDto } from "@/types/api";

/**
 * File d'attente du DG : les sections soumises, et celles validees par le comite de pilotage qu'il
 * n'a pas encore approuvees. Le DG n'attend pas l'admin : sa validation suffit a faire entrer une
 * section dans les documents consolides. Rangees dans l'ordre ou il les relit — direction par direction, puis dans l'ordre du canevas.
 * Partagee par le menu, le tableau de bord et l'onglet « À valider », seul endroit ou le DG valide.
 */
export function attendLaDg(s: SubmissionSummaryDto): boolean {
  return s.status === "SUBMITTED" || (s.status === "VALIDATED" && !s.dgApprovedAt);
}

export function fileDuDg(submissions: SubmissionSummaryDto[] | undefined): SubmissionSummaryDto[] {
  return (submissions ?? [])
    .filter(attendLaDg)
    .sort((a, b) => a.groupName.localeCompare(b.groupName) || a.sectionOrder - b.sectionOrder);
}
