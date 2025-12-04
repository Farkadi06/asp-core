package com.asp.auth.controller;

import com.asp.auth.dto.AuthResponse;
import com.asp.auth.dto.DevLoginRequest;
import com.asp.auth.repository.UserRepository;
import com.asp.auth.service.AuthService;
import com.asp.auth.util.CookieUtils;
import com.asp.auth.domain.User;
import com.asp.auth.domain.UserSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Development authentication controller.
 * 
 * NOTE: This endpoint is only for development.
 * In production, this will be replaced by Google OAuth.
 */
@RestController
@RequestMapping("/auth/dev")
@RequiredArgsConstructor
@Slf4j
public class DevAuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<AuthResponse> devLogin(@Valid @RequestBody DevLoginRequest request) {
        log.debug("Dev login request for email: {}", request.getEmail());

        // Create user if missing
        User user = authService.createUserIfMissing(request.getEmail(), request.getDisplayName());

        // Update last login timestamp
        user.setLastLoginAt(java.time.Instant.now());
        userRepository.save(user); // Save the updated lastLoginAt

        // Create session
        UserSession session = authService.createSession(user);

        // Build session cookie
        var sessionCookie = CookieUtils.buildSessionCookie(session.getSessionToken());

        // Build response
        AuthResponse response = AuthResponse.builder()
                .authenticated(true)
                .email(user.getEmail())
                .userId(user.getId())
                .tenantId(user.getTenant().getId())
                .build();

        log.info("Dev login successful for user: {} (tenant: {})", user.getEmail(), user.getTenant().getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
                .body(response);
    }
}

