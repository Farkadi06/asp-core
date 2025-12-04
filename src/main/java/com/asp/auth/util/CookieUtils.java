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

    /**
     * Builds a session cookie with the provided token.
     * 
     * Cookie settings:
     * - HttpOnly: true (prevents JavaScript access)
     * - Secure: true (HTTPS only)
     * - Path: "/"
     * - Max-Age: 14 days
     * - SameSite: Strict
     */
    public static ResponseCookie buildSessionCookie(String token) {
        return ResponseCookie.from(SESSION_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .path(COOKIE_PATH)
                .maxAge(Duration.ofDays(SESSION_DURATION_DAYS))
                .sameSite("Strict")
                .build();
    }

    /**
     * Builds a cookie to clear/delete the session cookie.
     */
    public static ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .sameSite("Strict")
                .build();
    }
}

