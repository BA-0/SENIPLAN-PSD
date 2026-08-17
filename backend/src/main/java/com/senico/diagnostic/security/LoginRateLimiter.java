package com.senico.diagnostic.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting simple en memoire sur les tentatives de login, par cle (username+IP).
 * Suffisant pour une seule instance backend ; a remplacer par Redis en cas de scale-out.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowMs;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.security.login-rate-limit-attempts}") int maxAttempts,
            @Value("${app.security.login-rate-limit-window-seconds}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowSeconds * 1000;
    }

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (Instant.now().toEpochMilli() - attempt.windowStart > windowMs) {
            attempts.remove(key);
            return false;
        }
        return attempt.count.get() >= maxAttempts;
    }

    public void recordFailure(String key) {
        attempts.compute(key, (k, existing) -> {
            long now = Instant.now().toEpochMilli();
            if (existing == null || now - existing.windowStart > windowMs) {
                return new Attempt(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private static class Attempt {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(1);

        Attempt(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
