package com.asp.auth.util;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Utility class for managing session cookies.
 */
public class CookieUtils {

    private static final String SESSION_COOKIE_NAME = "asp_session";
    private static final int SESSION_DURATION_DAYS = 14;
    private static final String COOKIE_PATH = "/";
    
    // For local development, set secure=false. In production, this should be true.
    // You can configure this via environment variable or application properties.
    private static final boolean COOKIE_SECURE = 
            Boolean.parseBoolean(System.getenv().getOrDefault("COOKIE_SECURE", "false"));

    /**
     * Builds a session cookie with the provided token.
     *
     * Development configuration:
     * - sameSite=Lax (works with localhost and doesn't require HTTPS)
     * - secure=false for dev (set COOKIE_SECURE=true in production/HTTPS)
     * 
     * Note: Domain is NOT set - allows cookie to work across localhost ports.
     * SameSite=Lax is used instead of None because Chrome rejects SameSite=None
     * cookies with Secure=false.
     */
    public static ResponseCookie buildSessionCookie(String token) {
        return ResponseCookie.from(SESSION_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)        // dev only; use true in prod
                .sameSite("Lax")      // IMPORTANT: fix Chrome cookie rejection
                .path(COOKIE_PATH)
                // domain removed intentionally for localhost
                .maxAge(Duration.ofDays(SESSION_DURATION_DAYS))
                .build();
    }

    /**
     * Builds a cookie to clear/delete the session cookie.
     */
    public static ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}

