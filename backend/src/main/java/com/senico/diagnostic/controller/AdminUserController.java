package com.senico.diagnostic.controller;

import com.senico.diagnostic.dto.group.ResetPasswordResponse;
import com.senico.diagnostic.dto.user.CreateUserAccountRequest;
import com.senico.diagnostic.dto.user.UserAccountDto;
import com.senico.diagnostic.service.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestion des comptes utilisateurs. Reserve a ROLE_ADMIN par SecurityConfig : donner ou reprendre
 * un acces reste de l'administration technique, y compris pour le compte du DG.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAccountService userAccountService;

    /** Tous les comptes, chefs de groupe compris : la seule vue d'ensemble des acces. */
    @GetMapping
    public ResponseEntity<List<UserAccountDto>> listAccounts() {
        return ResponseEntity.ok(userAccountService.listAccounts());
    }

    /** Le mot de passe genere n'est renvoye qu'ici, et n'est plus relisible ensuite. */
    @PostMapping
    public ResponseEntity<UserAccountDto> create(@Valid @RequestBody CreateUserAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAccountService.create(request));
    }

    /** Genere un nouveau mot de passe et le renvoie une seule fois. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountService.resetPassword(id));
    }
}
