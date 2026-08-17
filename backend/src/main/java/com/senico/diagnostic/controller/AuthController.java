package com.senico.diagnostic.controller;

import com.senico.diagnostic.dto.auth.AuthResponse;
import com.senico.diagnostic.dto.auth.LoginRequest;
import com.senico.diagnostic.dto.auth.RefreshRequest;
import com.senico.diagnostic.security.UserPrincipal;
import com.senico.diagnostic.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, resolveClientIp(httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserPrincipal> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(principal);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
