package com.senico.diagnostic.dto.section;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Approbation par lot : les sections cochees par le DG dans la liste des soumissions, avec un
 * commentaire commun facultatif. La liste est explicite — une selection vide est une erreur,
 * pas un raccourci pour « tout », que la route {@code /dg-approvals/all} assume seule.
 */
public record DgBatchApprovalRequest(
        @NotEmpty(message = "Aucune section selectionnee") @Valid List<DgSectionTarget> targets,
        String comment
) {
}
