package com.senico.diagnostic.controller;

import com.senico.diagnostic.dto.dashboard.ActivityEntryDto;
import com.senico.diagnostic.dto.dashboard.AdminDashboardDto;
import com.senico.diagnostic.dto.dashboard.MatrixCellDto;
import com.senico.diagnostic.dto.dashboard.SubmissionSummaryDto;
import com.senico.diagnostic.dto.section.SectionContentResponse;
import com.senico.diagnostic.service.AdminDashboardService;
import com.senico.diagnostic.service.SectionEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tableau de bord admin : KPIs, heatmap groupes x sections, flux d'activite, vue comparative.
 * Route protegee ROLE_ADMIN (cf. SecurityConfig : /api/v1/admin/**).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final SectionEngineService sectionEngineService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDto> dashboard() {
        return ResponseEntity.ok(adminDashboardService.dashboard());
    }

    @GetMapping("/matrix")
    public ResponseEntity<List<MatrixCellDto>> matrix() {
        return ResponseEntity.ok(adminDashboardService.matrix());
    }

    @GetMapping("/submissions")
    public ResponseEntity<List<SubmissionSummaryDto>> submissions() {
        return ResponseEntity.ok(adminDashboardService.submissions());
    }

    @GetMapping("/activity")
    public ResponseEntity<List<ActivityEntryDto>> activity(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(adminDashboardService.recentActivity(limit));
    }

    @GetMapping("/compare")
    public ResponseEntity<List<SectionContentResponse>> compare(
            @RequestParam String sectionCode,
            @RequestParam List<Long> groupIds) {
        return ResponseEntity.ok(sectionEngineService.compare(sectionCode, groupIds));
    }
}
