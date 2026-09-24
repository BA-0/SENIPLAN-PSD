package com.senico.diagnostic.controller;

import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.section.AdminReviewRequest;
import com.senico.diagnostic.dto.section.BulkValidationResponse;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.SectionEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Premier niveau de validation a l'echelle de la campagne : valider en un geste toutes les
 * sections soumises, toutes directions confondues, plutot que direction par direction depuis
 * {@code AdminSectionController}.
 *
 * <p>Admin et direction generale, comme la validation section par section (regle /admin/** de
 * SecurityConfig).</p>
 */
@RestController
@RequestMapping("/api/v1/admin/validations")
@RequiredArgsConstructor
public class AdminValidationController {

    private final SectionEngineService sectionEngineService;
    private final UserRepository userRepository;

    /** Valide toutes les sections au statut « Soumis », toutes directions confondues. */
    @PostMapping("/all")
    public ResponseEntity<BulkValidationResponse> validateAllSubmitted(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) AdminReviewRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        String comment = request != null ? request.comment() : null;
        int validated = sectionEngineService.adminValidateAllSubmittedEverywhere(comment, user);
        return ResponseEntity.ok(new BulkValidationResponse(validated));
    }
}
