package com.senico.diagnostic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private final SecretKey signingKey;
    /** Immuable et sur en concurrence : construit une fois plutot qu'a chaque lecture de jeton. */
    private final JwtParser parser;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(signingKey).build();
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(UserPrincipal user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .claim("groupId", user.getGroupId())
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UserPrincipal user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        String username = extractUsername(token);
        return username.equals(expectedUsername) && !isTokenExpired(token);
    }

    /**
     * Claims d'un jeton d'acces, lus en une seule verification (signature et expiration) ; null pour
     * un jeton d'un autre type. Leve une {@link io.jsonwebtoken.JwtException} si le jeton est
     * invalide ou expire.
     */
    public Claims parseAccessToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        return "access".equals(claims.get("type", String.class)) ? claims : null;
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parser.parseSignedClaims(token).getPayload());
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
