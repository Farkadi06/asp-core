package com.asp.auth.security;

import com.asp.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authentication filter for dashboard session-based authentication.
 * Reads "asp_session" cookie and validates session token.
 * 
 * IMPORTANT: This filter only activates on /dashboard/** and /auth/** routes.
 * It does NOT affect /v1/** API key authentication.
 */
@Slf4j
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String SESSION_COOKIE_NAME = "asp_session";
    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Read session cookie
        String sessionToken = extractSessionToken(request);

        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            // No session cookie - continue filter chain (unauthenticated)
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Call authService.findValidSession(sessionToken)
            // This method checks:
            // - Session exists
            // - Session is not invalidated (invalidatedAt == null)
            // - Session is not expired (expiresAt is in the future)
            var sessionOpt = authService.findValidSession(sessionToken);

            if (sessionOpt.isEmpty()) {
                // Invalid, expired, or invalidated session - continue without authentication
                // This is silent to avoid leaking session validity details
                log.debug("No valid session found for token (may be invalidated, expired, or not found)");
                filterChain.doFilter(request, response);
                return;
            }

            var session = sessionOpt.get();
            var user = session.getUser();

            // Build UserPrincipal
            UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getTenant().getId(),
                user.getEmail()
            );

            // Create authentication token
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,                    // principal: UserPrincipal
                null,                         // credentials: null
                List.of(new SimpleGrantedAuthority("ROLE_USER"))  // authorities: ROLE_USER
            );

            // Set authentication in SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user {} with session token", user.getEmail());

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Error processing session authentication", e);
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
        }
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

