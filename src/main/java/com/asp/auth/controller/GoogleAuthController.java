package com.asp.auth.controller;

import com.asp.auth.dto.GoogleUserInfo;
import com.asp.auth.service.AuthService;
import com.asp.auth.service.GoogleOAuthService;
import com.asp.auth.util.CookieUtils;
import com.asp.auth.domain.User;
import com.asp.auth.domain.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Controller for Google OAuth 2.0 authentication.
 */
@RestController
@RequestMapping("/auth/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthController {

    private final GoogleOAuthService googleOAuthService;
    private final AuthService authService;

    /**
     * Initiates Google OAuth login flow.
     * Redirects user to Google's authorization page.
     */
    @GetMapping("/login")
    public RedirectView googleLogin() {
        String redirectUrl = googleOAuthService.buildGoogleRedirectUrl();
        log.debug("Redirecting to Google OAuth: {}", redirectUrl);
        return new RedirectView(redirectUrl);
    }

    /**
     * Handles Google OAuth callback after user grants permission.
     * Exchanges authorization code for user info, creates/updates user, and issues session cookie.
     */
    @GetMapping("/callback")
    @Transactional
    public void googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state,
            HttpServletResponse response) throws Exception {

        if (error != null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Google OAuth authentication failed: " + error);
            return;
        }

        if (code == null || code.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing authorization code");
            return;
        }

        try {
            // Exchange code for user info
            GoogleUserInfo userInfo = googleOAuthService.exchangeCodeForUser(code);
            log.debug("Google OAuth successful for email: {}", userInfo.getEmail());

            // Handle login
            User user = authService.handleGoogleLogin(userInfo);

            // Create session
            UserSession session = authService.createSession(user);

            // Build session cookie
            var sessionCookie = CookieUtils.buildSessionCookie(session.getSessionToken());

            log.info("Google login successful for user: {} (tenant: {})",
                     user.getEmail(), user.getTenant().getId());

            // Add cookie manually (avoids Spring rewriting issues)
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());

            // Redirect manually
            String frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:7000");
            response.sendRedirect(frontendUrl + "/dashboard");

        } catch (Exception e) {
            log.error("Google OAuth callback error", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed: " + e.getMessage());
        }
    }
}

