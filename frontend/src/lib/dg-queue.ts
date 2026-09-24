import type { SubmissionSummaryDto } from "@/types/api";

/**
 * File d'attente du DG : les sections soumises, et celles validees par le comite de pilotage qu'il
 * n'a pas encore approuvees. Le DG n'attend pas l'admin : sa validation suffit a faire entrer une
 * section dans les documents consolides. Rangees dans l'ordre ou il les relit — direction par direction, puis dans l'ordre du canevas.
 * Partagee par le tableau de bord (« Commencer la revue ») et la page d'une section (« suivante »),
 * pour que les deux designent la meme section.
 */
export function attendLaDg(s: SubmissionSummaryDto): boolean {
  return s.status === "SUBMITTED" || (s.status === "VALIDATED" && !s.dgApprovedAt);
}

export function fileDuDg(submissions: SubmissionSummaryDto[] | undefined): SubmissionSummaryDto[] {
  return (submissions ?? [])
    .filter(attendLaDg)
    .sort((a, b) => a.groupName.localeCompare(b.groupName) || a.sectionOrder - b.sectionOrder);
}

/**
 * Section a ouvrir apres celle-ci : la suivante dans la file, sinon la premiere restante. La
 * section courante est exclue, qu'elle soit encore en attente (liste pas encore rafraichie) ou non.
 */
export function suivanteDansLaFile(
  file: SubmissionSummaryDto[],
  groupId: number,
  code: string
): SubmissionSummaryDto | null {
  const autres = file.filter((s) => !(s.groupId === groupId && s.sectionCode === code));
  if (autres.length === 0) return null;
  const courante = file.findIndex((s) => s.groupId === groupId && s.sectionCode === code);
  if (courante === -1) return autres[0];
  return file.slice(courante + 1).find((s) => autres.includes(s)) ?? autres[0];
}

export function lienSection(s: Pick<SubmissionSummaryDto, "groupId" | "sectionCode">): string {
  return `/admin/groups/${s.groupId}/sections/${s.sectionCode}`;
}
