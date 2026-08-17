package com.senico.diagnostic.controller;

import com.senico.diagnostic.dto.group.CreateWorkGroupRequest;
import com.senico.diagnostic.dto.group.ResetPasswordResponse;
import com.senico.diagnostic.dto.group.UpdateWorkGroupRequest;
import com.senico.diagnostic.dto.group.WorkGroupDto;
import com.senico.diagnostic.service.WorkGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gestion des groupes de travail. Reserve a l'admin (cf. SecurityConfig : /api/v1/groups/** -> ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class WorkGroupController {

    private final WorkGroupService workGroupService;

    @GetMapping
    public ResponseEntity<List<WorkGroupDto>> listAll() {
        return ResponseEntity.ok(workGroupService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkGroupDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(workGroupService.getById(id));
    }

    @PostMapping
    public ResponseEntity<WorkGroupDto> create(@Valid @RequestBody CreateWorkGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workGroupService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkGroupDto> update(@PathVariable Long id, @Valid @RequestBody UpdateWorkGroupRequest request) {
        return ResponseEntity.ok(workGroupService.update(id, request));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        workGroupService.setEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return ResponseEntity.ok(workGroupService.resetLeaderPassword(id));
    }
}
