package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.auth.AuthResponse;
import com.senico.diagnostic.dto.auth.ChangePasswordRequest;
import com.senico.diagnostic.dto.auth.LoginRequest;
import com.senico.diagnostic.exception.RateLimitExceededException;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.security.JwtService;
import com.senico.diagnostic.security.LoginRateLimiter;
import com.senico.diagnostic.security.UserPrincipal;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        String rateLimitKey = request.username() + "|" + clientIp;

        if (rateLimiter.isBlocked(rateLimitKey)) {
            throw new RateLimitExceededException(
                    "Trop de tentatives de connexion. Veuillez reessayer dans quelques instants.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException ex) {
            rateLimiter.recordFailure(rateLimitKey);
            throw ex;
        }

        rateLimiter.reset(rateLimitKey);

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        return buildAuthResponse(principal, user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        String tokenType;
        String username;
        try {
            tokenType = jwtService.extractTokenType(refreshToken);
            username = jwtService.extractUsername(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Refresh token invalide");
        }

        if (!"refresh".equals(tokenType) || !jwtService.isTokenValid(refreshToken, username)) {
            throw new BadCredentialsException("Refresh token invalide ou expire");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Utilisateur introuvable"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Compte desactive");
        }

        UserPrincipal principal = new UserPrincipal(user);
        return buildAuthResponse(principal, user);
    }

    /**
     * Le titulaire choisit son mot de passe. C'est la seule ecriture qui remet
     * {@code mustChangePassword} a faux : tant qu'un mot de passe vient d'un tiers, le compte
     * reste bloque sur cet ecran (cf. PasswordChangeGuardFilter).
     *
     * <p>Rend une session complete : les jetons courants restent valides, mais l'appelant a
     * besoin d'un {@code user} a jour pour ne pas se voir renvoye sur le meme ecran.</p>
     */
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Utilisateur introuvable"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // Pas un echec de connexion : le compte est deja authentifie, et lui repondre
            // « identifiant ou mot de passe incorrect » l'enverrait chercher au mauvais endroit.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mot de passe actuel incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe doit etre different de l'actuel");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        return buildAuthResponse(new UserPrincipal(user), user);
    }

    private AuthResponse buildAuthResponse(UserPrincipal principal, User user) {
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .groupId(user.getGroup() != null ? user.getGroup().getId() : null)
                        .groupName(user.getGroup() != null ? user.getGroup().getName() : null)
                        .mustChangePassword(user.isMustChangePassword())
                        .build())
                .build();
    }
}
