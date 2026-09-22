package com.senico.diagnostic.controller;

import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.dto.group.PeerGroupDto;
import com.senico.diagnostic.dto.section.SectionContentResponse;
import com.senico.diagnostic.dto.section.SectionStatusSummary;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.WorkGroupRepository;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.SectionEngineService;
import com.senico.diagnostic.service.WorkGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * Consultation croisee entre directions, en lecture seule : chaque chef de groupe peut suivre
 * la saisie des autres directions (brouillons compris), sans jamais pouvoir la modifier.
 *
 * <p>Uniquement des GET : SecurityConfig refuse toute autre methode sur {@code /api/v1/peers/**}.
 * Les commentaires du comite de pilotage et du DG sont retires des reponses — ils s'adressent
 * a la direction concernee, pas a ses homologues.</p>
 */
@RestController
@RequestMapping("/api/v1/peers")
@RequiredArgsConstructor
public class PeerSectionController {

    private final WorkGroupService workGroupService;
    private final WorkGroupRepository workGroupRepository;
    private final SectionEngineService sectionEngineService;

    /** Directions actives, hors celle de l'utilisateur connecte. */
    @GetMapping("/groups")
    public ResponseEntity<List<PeerGroupDto>> listGroups(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(workGroupService.listAll().stream()
                .filter(g -> g.enabled() && !Objects.equals(g.id(), principal.getGroupId()))
                .map(g -> PeerGroupDto.builder()
                        .id(g.id())
                        .name(g.name())
                        .description(g.description())
                        .color(g.color())
                        .leaderFullName(g.leaderFullName())
                        .completionPercent(g.completionPercent())
                        .sectionsSubmitted(g.sectionsSubmitted())
                        .sectionsValidated(g.sectionsValidated())
                        .lastActivityAt(g.lastActivityAt())
                        .build())
                .toList());
    }

    @GetMapping("/groups/{groupId}/sections")
    public ResponseEntity<List<SectionStatusSummary>> listStatuses(@PathVariable Long groupId) {
        requireEnabledGroup(groupId);
        return ResponseEntity.ok(sectionEngineService.listStatuses(groupId).stream()
                .map(PeerSectionController::withoutComments)
                .toList());
    }

    @GetMapping("/groups/{groupId}/sections/{code}")
    public ResponseEntity<SectionContentResponse> getContent(@PathVariable Long groupId, @PathVariable String code) {
        requireEnabledGroup(groupId);
        return ResponseEntity.ok(withoutComments(sectionEngineService.getContent(groupId, code)));
    }

    /** Une direction desactivee disparait de la liste : elle ne doit pas rester lisible par son URL. */
    private void requireEnabledGroup(Long groupId) {
        boolean enabled = workGroupRepository.findById(groupId).map(WorkGroup::isEnabled).orElse(false);
        if (!enabled) {
            throw new ResourceNotFoundException("Groupe introuvable : " + groupId);
        }
    }

    private static SectionStatusSummary withoutComments(SectionStatusSummary s) {
        return SectionStatusSummary.builder()
                .sectionId(s.sectionId())
                .code(s.code())
                .title(s.title())
                .order(s.order())
                .status(s.status())
                .submittedAt(s.submittedAt())
                .validatedAt(s.validatedAt())
                .dgApprovedAt(s.dgApprovedAt())
                .lastActivityAt(s.lastActivityAt())
                .build();
    }

    private static SectionContentResponse withoutComments(SectionContentResponse r) {
        return SectionContentResponse.builder()
                .sectionId(r.sectionId())
                .code(r.code())
                .title(r.title())
                .order(r.order())
                .type(r.type())
                .groupId(r.groupId())
                .groupName(r.groupName())
                .status(r.status())
                // Toujours verrouillee pour le lecteur, quel que soit le statut reel.
                .locked(true)
                .content(r.content())
                .version(r.version())
                .updatedAt(r.updatedAt())
                .submittedAt(r.submittedAt())
                .validatedAt(r.validatedAt())
                .dgApprovedAt(r.dgApprovedAt())
                .lastActivityAt(r.lastActivityAt())
                .build();
    }
}
