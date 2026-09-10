package com.senico.diagnostic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Tant qu'un compte porte un mot de passe attribue par un tiers, il ne peut rien faire d'autre
 * que se connecter et le remplacer. La regle est posee ici plutot que dans l'interface : une
 * redirection cote navigateur se contourne en tapant une URL, et le mot de passe remis en main
 * propre resterait alors utilisable indefiniment.
 *
 * <p>Le drapeau est relu en base a chaque requete (le principal est reconstruit par
 * {@link CustomUserDetailsService}) : le changement prend effet immediatement, sans attendre
 * l'expiration du jeton d'acces.</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeGuardFilter extends OncePerRequestFilter {

    /**
     * Ce qu'il faut pouvoir appeler pour, justement, changer son mot de passe.
     *
     * <p>Compare via les matchers de Spring Security plutot qu'a {@code getServletPath()} :
     * ce dernier vaut la chaine vide selon la facon dont la requete est routee, et le garde
     * bloquerait alors le changement de mot de passe lui-meme.</p>
     */
    private static final List<RequestMatcher> ALLOWED_PATHS = List.of(
            new AntPathRequestMatcher("/api/v1/auth/**"),
            new AntPathRequestMatcher("/ws/**"),
            new AntPathRequestMatcher("/actuator/health")
    );

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof UserPrincipal user
                && user.isMustChangePassword()
                && !isAllowed(request)) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "status", HttpServletResponse.SC_FORBIDDEN,
                    "code", "PASSWORD_CHANGE_REQUIRED",
                    "message", "Vous devez changer votre mot de passe avant d'utiliser l'application"
            )));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(HttpServletRequest request) {
        return ALLOWED_PATHS.stream().anyMatch(matcher -> matcher.matches(request));
    }
}
