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
    public ResponseEntity<?> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state) {

        // Handle OAuth errors
        if (error != null) {
            log.warn("Google OAuth error: {}", error);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Google OAuth authentication failed: " + error);
        }

        // Validate authorization code
        if (code == null || code.trim().isEmpty()) {
            log.warn("Missing authorization code in Google OAuth callback");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing authorization code");
        }

        try {
            // Exchange code for user info
            GoogleUserInfo userInfo = googleOAuthService.exchangeCodeForUser(code);
            log.debug("Google OAuth successful for email: {}", userInfo.getEmail());

            // Handle Google login (create or update user)
            User user = authService.handleGoogleLogin(userInfo);

            // Create session
            UserSession session = authService.createSession(user);

            // Build session cookie
            var sessionCookie = CookieUtils.buildSessionCookie(session.getSessionToken());

            log.info("Google login successful for user: {} (tenant: {})", 
                    user.getEmail(), user.getTenant().getId());

            // Redirect to dashboard
            RedirectView redirectView = new RedirectView("/dashboard");
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
                    .header(HttpHeaders.LOCATION, "/dashboard")
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("Google OAuth validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Authentication failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Google OAuth callback error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Authentication failed: " + e.getMessage());
        }
    }
}

