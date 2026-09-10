package com.senico.diagnostic.controller;

import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.section.BulkApprovalResponse;
import com.senico.diagnostic.dto.section.DgApprovalRequest;
import com.senico.diagnostic.dto.section.DgBatchApprovalRequest;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.SectionEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Arbitrage du DG a l'echelle de la campagne, et non plus d'une direction a la fois.
 *
 * <p>Le canevas compte une vingtaine de sections par direction : sur une quinzaine de
 * directions, reprendre l'approbation section par section — ou meme direction par direction
 * depuis {@code AdminSectionController} — represente des dizaines de passages pour une
 * decision que le DG prend le plus souvent en bloc. Ces deux routes lui rendent le geste :
 * tout ce qui attend, ou seulement ce qu'il a coche dans la liste des soumissions.</p>
 *
 * <p>Reserve au DG dans SecurityConfig, comme les routes {@code dg-approval} : l'admin ne peut
 * pas s'accorder a lui-meme l'entree dans les documents consolides.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/dg-approvals")
@RequiredArgsConstructor
public class AdminDgApprovalController {

    private final SectionEngineService sectionEngineService;
    private final UserRepository userRepository;

    /** Approuve les sections cochees par le DG, toutes directions confondues. */
    @PostMapping("/selection")
    public ResponseEntity<BulkApprovalResponse> approveSelection(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DgBatchApprovalRequest request) {
        User dg = resolveDg(principal);
        int approved = sectionEngineService.dgApproveSelection(request.targets(), request.comment(), dg);
        return ResponseEntity.ok(new BulkApprovalResponse(approved));
    }

    /** Approuve tout ce qui a ete valide par le comite de pilotage et attend encore le DG. */
    @PostMapping("/all")
    public ResponseEntity<BulkApprovalResponse> approveAllPending(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) DgApprovalRequest request) {
        User dg = resolveDg(principal);
        String comment = request != null ? request.comment() : null;
        int approved = sectionEngineService.dgApproveAllPending(comment, dg);
        return ResponseEntity.ok(new BulkApprovalResponse(approved));
    }

    private User resolveDg(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
