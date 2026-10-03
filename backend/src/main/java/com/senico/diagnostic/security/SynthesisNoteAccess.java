package com.senico.diagnostic.security;

import com.senico.diagnostic.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qui corrige la note de synthese (demande client du 03/10/2026) : la Direction Generale, et les
 * comptes nommes dans {@code app.synthesis-note.editors} — par defaut {@code dir.generale}, le
 * compte de chef de groupe de la Direction Generale, qui n'a pas acces au pilotage.
 *
 * <p>Utilise par SecurityConfig pour garder les routes, et a la connexion pour que l'interface
 * affiche l'onglet aux seuls comptes concernes.</p>
 */
@Component
public class SynthesisNoteAccess {

    private final Set<String> editors;

    public SynthesisNoteAccess(@Value("${app.synthesis-note.editors:dir.generale}") List<String> editors) {
        this.editors = editors.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean canEdit(String username, Role role) {
        return role == Role.DIRECTEUR_GENERAL
                || (username != null && editors.contains(username.toLowerCase(Locale.ROOT)));
    }

    public boolean canEdit(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        boolean dg = authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + Role.DIRECTEUR_GENERAL.name()).equals(a.getAuthority()));
        return canEdit(authentication.getName(), dg ? Role.DIRECTEUR_GENERAL : null);
    }
}
