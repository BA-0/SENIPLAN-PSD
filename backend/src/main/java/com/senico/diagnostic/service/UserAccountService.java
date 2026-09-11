package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.Role;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.dto.group.ResetPasswordResponse;
import com.senico.diagnostic.dto.user.CreateUserAccountRequest;
import com.senico.diagnostic.dto.user.UserAccountDto;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
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
 * Comptes utilisateurs, vus depuis l'administration : creation, liste et reinitialisation de
 * mot de passe, pour tous les roles.
 *
 * <p>La creation d'une direction ({@link WorkGroupService}) cree toujours son chef de groupe :
 * une direction sans personne pour la remplir n'aurait pas de sens. Ce service repond a l'autre
 * besoin — ajouter un compte a une direction existante, ou un compte transverse — et donne la
 * vue d'ensemble qui manquait, la ou l'ecran des directions ne montre qu'un titulaire par
 * direction.</p>
 */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private static final Map<Role, String> ROLE_LABELS = Map.of(
            Role.ADMIN, "Administrateur",
            Role.DIRECTEUR_GENERAL, "Direction Générale",
            Role.GROUP_LEADER, "Chef de groupe"
    );

    private final UserRepository userRepository;
    private final WorkGroupRepository workGroupRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGeneratorService passwordGeneratorService;

    @Transactional(readOnly = true)
    public List<UserAccountDto> listAccounts() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing((User u) -> u.getRole().ordinal())
                        .thenComparing(User::getUsername))
                .map(this::toDto)
                .toList();
    }

    /**
     * Cree un compte et rend son mot de passe une seule fois, a charge pour l'admin de le
     * transmettre. Le titulaire devra le remplacer des sa premiere connexion : ce mot de passe
     * la est passe par un tiers.
     */
    @Transactional
    public UserAccountDto create(CreateUserAccountRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet identifiant existe deja");
        }

        WorkGroup group = resolveGroup(request);

        String rawPassword = request.password() != null
                ? request.password()
                : passwordGeneratorService.generate();

        User created = userRepository.save(User.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(request.fullName())
                .role(request.role())
                .group(group)
                .enabled(true)
                .mustChangePassword(true)
                .build());

        return toDto(created).withTemporaryPassword(rawPassword);
    }

    /**
     * Nouveau mot de passe, renvoye une seule fois : c'est ainsi qu'un compte recoit son acces,
     * sans qu'aucun mot de passe n'ait ete ecrit dans le code ni transmis par un autre canal.
     *
     * <p>Vaut aussi pour les chefs de groupe. L'ecran des directions garde son propre bouton,
     * qui ne touche que le titulaire de la direction ; depuis qu'une direction peut compter
     * plusieurs comptes, lui seul ne suffit plus.</p>
     */
    @Transactional
    public ResetPasswordResponse resetPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));

        String rawPassword = passwordGeneratorService.generate();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        return new ResetPasswordResponse(user.getUsername(), rawPassword);
    }

    /**
     * Change l'identifiant de connexion, mot de passe inchange. Les jetons en cours portent
     * l'ancien identifiant et cessent donc de valoir : le titulaire se reconnecte avec le nouveau.
     */
    @Transactional
    public UserAccountDto changeUsername(Long userId, String newUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));

        // Le compte lui-meme est exclu : il peut par exemple ne changer que la casse du sien.
        boolean prisParUnAutre = userRepository.findByUsername(newUsername)
                .filter(other -> !other.getId().equals(userId))
                .isPresent();
        if (prisParUnAutre) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet identifiant existe deja");
        }

        user.setUsername(newUsername);
        return toDto(userRepository.save(user));
    }

    /**
     * Le rattachement a une direction est ce qui decide du canevas rempli par le compte : exige
     * pour un chef de groupe, refuse pour les autres roles, ou il n'aurait aucun effet visible
     * tout en laissant croire le contraire.
     */
    private WorkGroup resolveGroup(CreateUserAccountRequest request) {
        if (request.role() != Role.GROUP_LEADER) {
            if (request.groupId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un compte " + ROLE_LABELS.get(request.role()) + " ne se rattache a aucune direction");
            }
            return null;
        }
        if (request.groupId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La direction est requise pour un chef de groupe");
        }
        return workGroupRepository.findById(request.groupId())
                .orElseThrow(() -> new ResourceNotFoundException("Direction introuvable : " + request.groupId()));
    }

    private UserAccountDto toDto(User user) {
        return new UserAccountDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                ROLE_LABELS.getOrDefault(user.getRole(), user.getRole().name()),
                user.getGroup() != null ? user.getGroup().getId() : null,
                user.getGroup() != null ? user.getGroup().getName() : null,
                user.isEnabled(),
                user.isMustChangePassword(),
                user.getLastLoginAt(),
                null);
    }
}
