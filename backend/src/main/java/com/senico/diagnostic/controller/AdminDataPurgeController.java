package com.senico.diagnostic.controller;

import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.section.DataPurgeResponse;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.DataPurgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Effacement definitif des saisies (donnees d'essai). Reserve a ROLE_ADMIN par SecurityConfig :
 * c'est de l'administration technique, que le DG n'exerce pas.
 */
@RestController
@RequestMapping("/api/v1/admin/data-purge")
@RequiredArgsConstructor
public class AdminDataPurgeController {

    private final DataPurgeService dataPurgeService;
    private final UserRepository userRepository;

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<DataPurgeResponse> purgeGroup(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long groupId) {
        return ResponseEntity.ok(dataPurgeService.purgeGroup(groupId, currentUser(principal)));
    }

    @DeleteMapping("/groups")
    public ResponseEntity<DataPurgeResponse> purgeAllGroups(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dataPurgeService.purgeAllGroups(currentUser(principal)));
    }

    private User currentUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
