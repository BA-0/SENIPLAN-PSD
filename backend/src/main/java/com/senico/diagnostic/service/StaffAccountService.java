package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.Role;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.group.ResetPasswordResponse;
import com.senico.diagnostic.dto.user.StaffAccountDto;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Comptes transverses : admin et direction generale. Les chefs de groupe, eux, se gerent
 * depuis leur direction ({@link WorkGroupService}), dont ils sont indissociables.
 */
@Service
@RequiredArgsConstructor
public class StaffAccountService {

    private static final Map<Role, String> ROLE_LABELS = Map.of(
            Role.ADMIN, "Administrateur",
            Role.DIRECTEUR_GENERAL, "Direction Générale",
            Role.GROUP_LEADER, "Chef de groupe"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGeneratorService passwordGeneratorService;

    @Transactional(readOnly = true)
    public List<StaffAccountDto> listStaffAccounts() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() != Role.GROUP_LEADER)
                .sorted(Comparator.comparing(User::getUsername))
                .map(this::toDto)
                .toList();
    }

    /**
     * Nouveau mot de passe, renvoye une seule fois : c'est ainsi que le DG recoit son acces,
     * sans qu'aucun mot de passe n'ait ete ecrit dans le code ni transmis par un autre canal.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));

        // Un chef de groupe se reinitialise depuis sa direction, la ou le lien avec le groupe
        // est visible : router les deux chemins vers le meme compte prete a confusion.
        if (user.getRole() == Role.GROUP_LEADER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe d'un chef de groupe se reinitialise depuis sa direction");
        }

        String rawPassword = passwordGeneratorService.generate();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        return new ResetPasswordResponse(user.getUsername(), rawPassword);
    }

    private StaffAccountDto toDto(User user) {
        return new StaffAccountDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                ROLE_LABELS.getOrDefault(user.getRole(), user.getRole().name()),
                user.isEnabled(),
                user.getLastLoginAt());
    }
}
