package com.senico.diagnostic.controller;

import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.cycle.GroupCycleSectionContentDto;
import com.senico.diagnostic.dto.cycle.GroupCycleSummaryDto;
import com.senico.diagnostic.dto.section.SectionStatusSummary;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.SectionEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cycles de saisie d'une direction : cloture (soumission complete -> nouveau cycle) et
 * consultation des cycles archives. Route protegee ROLE_ADMIN (cf. SecurityConfig, /api/v1/admin/**).
 */
@RestController
@RequestMapping("/api/v1/admin/groups/{groupId}/cycles")
@RequiredArgsConstructor
public class GroupCycleController {

    private final SectionEngineService sectionEngineService;
    private final UserRepository userRepository;

    @PostMapping("/new")
    public ResponseEntity<GroupCycleSummaryDto> startNewCycle(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long groupId) {
        User admin = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return ResponseEntity.ok(sectionEngineService.startNewCycle(groupId, admin));
    }

    @GetMapping
    public ResponseEntity<List<GroupCycleSummaryDto>> listCycles(@PathVariable Long groupId) {
        return ResponseEntity.ok(sectionEngineService.listCycles(groupId));
    }

    @GetMapping("/{cycleNumber}/sections")
    public ResponseEntity<List<SectionStatusSummary>> cycleSections(
            @PathVariable Long groupId, @PathVariable Integer cycleNumber) {
        return ResponseEntity.ok(sectionEngineService.getCycleSections(groupId, cycleNumber));
    }

    @GetMapping("/{cycleNumber}/sections/{code}")
    public ResponseEntity<GroupCycleSectionContentDto> cycleSectionContent(
            @PathVariable Long groupId, @PathVariable Integer cycleNumber, @PathVariable String code) {
        return ResponseEntity.ok(sectionEngineService.getCycleSectionContent(groupId, cycleNumber, code));
    }
}
