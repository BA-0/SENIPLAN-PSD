package com.senico.diagnostic.controller;

import com.senico.diagnostic.dto.psd.NarrativeBlockDto;
import com.senico.diagnostic.dto.psd.UpdateNarrativeBlockRequest;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.PsdNarrativeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Edition des blocs de texte narratif (Mot du DG, Preambule, ...) du "Document final PSD 2027-2031".
 * Reserve a l'admin (cf. SecurityConfig : /api/v1/admin/** -> ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin/psd-narrative")
@RequiredArgsConstructor
public class PsdNarrativeController {

    private final PsdNarrativeService psdNarrativeService;

    @GetMapping
    public ResponseEntity<List<NarrativeBlockDto>> listAll() {
        return ResponseEntity.ok(psdNarrativeService.listAll());
    }

    @PutMapping("/{key}")
    public ResponseEntity<NarrativeBlockDto> update(
            @PathVariable String key,
            @RequestBody UpdateNarrativeBlockRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(psdNarrativeService.update(key, request, principal.getUsername()));
    }
}
