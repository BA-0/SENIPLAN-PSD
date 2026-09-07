package com.senico.diagnostic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.section.AdminReviewRequest;
import com.senico.diagnostic.dto.section.BulkValidationResponse;
import com.senico.diagnostic.dto.section.SectionContentResponse;
import com.senico.diagnostic.dto.section.SectionRevisionContentResponse;
import com.senico.diagnostic.dto.section.SectionRevisionSummaryDto;
import com.senico.diagnostic.dto.section.SectionStatusSummary;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.SectionEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Suivi et revision des sections par l'admin, pour n'importe quel groupe
 * (y compris consultation des brouillons non soumis). Route protegee ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/groups/{groupId}/sections")
@RequiredArgsConstructor
public class AdminSectionController {

    private final SectionEngineService sectionEngineService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<SectionStatusSummary>> listStatuses(@PathVariable Long groupId) {
        return ResponseEntity.ok(sectionEngineService.listStatuses(groupId));
    }

    @GetMapping("/{code}")
    public ResponseEntity<SectionContentResponse> getContent(@PathVariable Long groupId, @PathVariable String code) {
        return ResponseEntity.ok(sectionEngineService.getContent(groupId, code));
    }

    @PostMapping("/{code}/review")
    public ResponseEntity<SectionContentResponse> review(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long groupId,
            @PathVariable String code,
            @Valid @RequestBody AdminReviewRequest request) {
        User admin = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return ResponseEntity.ok(sectionEngineService.adminReview(groupId, code, request, admin));
    }

    /** Valide en une fois toutes les sections soumises de la direction (cf. SectionEngineService). */
    @PostMapping("/validate-all")
    public ResponseEntity<BulkValidationResponse> validateAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long groupId,
            @RequestBody(required = false) AdminReviewRequest request) {
        User admin = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        String comment = request != null ? request.comment() : null;
        int validated = sectionEngineService.adminValidateAllSubmitted(groupId, comment, admin);
        return ResponseEntity.ok(new BulkValidationResponse(validated));
    }

    @PutMapping("/{code}/content")
    public ResponseEntity<SectionContentResponse> updateContent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long groupId,
            @PathVariable String code,
            @RequestBody JsonNode content) {
        User admin = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return ResponseEntity.ok(sectionEngineService.adminUpdateContent(groupId, code, content, admin));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<SectionContentResponse> reset(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long groupId,
            @PathVariable String code) {
        User admin = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return ResponseEntity.ok(sectionEngineService.adminReset(groupId, code, admin));
    }

    @GetMapping("/{code}/history")
    public ResponseEntity<List<SectionRevisionSummaryDto>> history(@PathVariable Long groupId, @PathVariable String code) {
        return ResponseEntity.ok(sectionEngineService.getHistory(groupId, code));
    }

    @GetMapping("/{code}/history/{version}")
    public ResponseEntity<SectionRevisionContentResponse> historyContent(
            @PathVariable Long groupId,
            @PathVariable String code,
            @PathVariable Integer version) {
        return ResponseEntity.ok(sectionEngineService.getHistoryContent(groupId, code, version));
    }
}
