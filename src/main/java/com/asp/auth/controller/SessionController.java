package com.asp.auth.controller;

import com.asp.auth.dto.AuthResponse;
import com.asp.auth.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for session management endpoints.
 */
@RestController
@RequestMapping("/auth/session")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            log.debug("No authenticated user found in session");
            return ResponseEntity.ok(AuthResponse.builder()
                    .authenticated(false)
                    .build());
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        AuthResponse response = AuthResponse.builder()
                .authenticated(true)
                .email(principal.email())
                .userId(principal.userId())
                .tenantId(principal.tenantId())
                .build();

        log.debug("Session check successful for user: {}", principal.email());

        return ResponseEntity.ok(response);
    }
}

