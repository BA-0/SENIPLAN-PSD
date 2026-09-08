package com.senico.diagnostic.controller;

import com.senico.diagnostic.dto.group.ResetPasswordResponse;
import com.senico.diagnostic.dto.user.StaffAccountDto;
import com.senico.diagnostic.service.StaffAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestion des comptes transverses (admin, direction generale), qui ne sont rattaches a aucun
 * groupe de travail et echappent donc a l'ecran des directions. Reserve a ROLE_ADMIN par
 * SecurityConfig : donner un acces reste de l'administration technique, y compris pour le
 * compte du DG.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final StaffAccountService staffAccountService;

    @GetMapping("/staff")
    public ResponseEntity<List<StaffAccountDto>> listStaffAccounts() {
        return ResponseEntity.ok(staffAccountService.listStaffAccounts());
    }

    /** Genere un nouveau mot de passe et le renvoie une seule fois, comme pour les chefs de groupe. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return ResponseEntity.ok(staffAccountService.resetPassword(id));
    }
}
