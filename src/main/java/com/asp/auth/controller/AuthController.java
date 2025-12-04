package com.asp.auth.controller;

import com.asp.auth.dto.LogoutResponse;
import com.asp.auth.service.AuthService;
import com.asp.auth.util.CookieUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for authentication operations (logout).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private static final String SESSION_COOKIE_NAME = "asp_session";
    private final AuthService authService;

    /**
     * Logout endpoint - invalidates session and clears cookie.
     * 
     * This endpoint is PUBLIC (permitAll) because it only destroys sessions.
     * It's idempotent: if no cookie is present, still returns success.
     */
    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<LogoutResponse> logout(HttpServletRequest request) {
        // Extract session token from cookie
        String sessionToken = extractSessionToken(request);

        if (sessionToken != null && !sessionToken.trim().isEmpty()) {
            // Invalidate session in database
            authService.invalidateSession(sessionToken);
            log.debug("Session invalidated for logout");
        } else {
            log.debug("No session cookie found during logout - idempotent success");
        }

        // Build clear cookie
        var clearCookie = CookieUtils.clearSessionCookie();

        // Return success response with Set-Cookie header to clear cookie
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(new LogoutResponse(true));
    }

    /**
     * Extracts session token from "asp_session" cookie.
     */
    private String extractSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}

