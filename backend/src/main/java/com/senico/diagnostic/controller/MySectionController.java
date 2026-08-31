package com.senico.diagnostic.controller;

import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.cycle.GroupCycleSectionContentDto;
import com.senico.diagnostic.dto.cycle.GroupCycleSummaryDto;
import com.senico.diagnostic.dto.dashboard.MyDashboardDto;
import com.senico.diagnostic.dto.section.SaveDraftRequest;
import com.senico.diagnostic.dto.section.SectionContentResponse;
import com.senico.diagnostic.dto.section.SectionStatusSummary;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.MyDashboardService;
import com.senico.diagnostic.service.SectionEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Espace de travail du chef de groupe : n'expose jamais d'ID de groupe en entree,
 * le groupe est toujours resolu depuis le principal authentifie (defense en profondeur
 * contre tout acces croise entre groupes).
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MySectionController {

    private final SectionEngineService sectionEngineService;
    private final MyDashboardService myDashboardService;
    private final UserRepository userRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<MyDashboardDto> dashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(myDashboardService.build(requireGroupId(principal)));
    }

    @GetMapping("/sections")
    public ResponseEntity<List<SectionStatusSummary>> listStatuses(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sectionEngineService.listStatuses(requireGroupId(principal)));
    }

    @GetMapping("/sections/{code}")
    public ResponseEntity<SectionContentResponse> getContent(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String code) {
        return ResponseEntity.ok(sectionEngineService.getContent(requireGroupId(principal), code));
    }

    @PutMapping("/sections/{code}/draft")
    public ResponseEntity<SectionContentResponse> saveDraft(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String code,
            @Valid @RequestBody SaveDraftRequest request) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(sectionEngineService.saveDraft(requireGroupId(principal), code, request.content(), user));
    }

    @PostMapping("/sections/{code}/submit")
    public ResponseEntity<SectionContentResponse> submit(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String code) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(sectionEngineService.submit(requireGroupId(principal), code, user));
    }

    /** Consultation, en lecture seule, des cycles de saisie precedemment clotures pour son propre groupe. */
    @GetMapping("/cycles")
    public ResponseEntity<List<GroupCycleSummaryDto>> listMyCycles(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sectionEngineService.listCycles(requireGroupId(principal)));
    }

    @GetMapping("/cycles/{cycleNumber}/sections")
    public ResponseEntity<List<SectionStatusSummary>> myCycleSections(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Integer cycleNumber) {
        return ResponseEntity.ok(sectionEngineService.getCycleSections(requireGroupId(principal), cycleNumber));
    }

    @GetMapping("/cycles/{cycleNumber}/sections/{code}")
    public ResponseEntity<GroupCycleSectionContentDto> myCycleSectionContent(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Integer cycleNumber, @PathVariable String code) {
        return ResponseEntity.ok(sectionEngineService.getCycleSectionContent(requireGroupId(principal), cycleNumber, code));
    }

    private Long requireGroupId(UserPrincipal principal) {
        if (principal.getGroupId() == null) {
            throw new ResourceNotFoundException("Ce compte n'est rattache a aucun groupe de travail");
        }
        return principal.getGroupId();
    }

    private User resolveUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
