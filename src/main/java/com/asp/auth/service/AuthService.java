package com.asp.auth.service;

import com.asp.auth.domain.User;
import com.asp.auth.domain.UserSession;
import com.asp.auth.repository.UserRepository;
import com.asp.auth.repository.UserSessionRepository;
import com.asp.domain.Tenant;
import com.asp.domain.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Authentication service for dashboard user sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final TenantRepository tenantRepository;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int SESSION_TOKEN_LENGTH = 64;
    private static final int SESSION_DURATION_DAYS = 14;

    /**
     * Finds a valid, non-expired, non-invalidated session by token.
     */
    @Transactional(readOnly = true)
    public Optional<UserSession> findValidSession(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Optional.empty();
        }

        Optional<UserSession> sessionOpt = userSessionRepository.findBySessionToken(token);

        if (sessionOpt.isEmpty()) {
            log.debug("Session not found for token");
            return Optional.empty();
        }

        UserSession session = sessionOpt.get();

        // Check if session is invalidated
        if (session.getInvalidatedAt() != null) {
            log.debug("Session {} is invalidated", session.getId());
            return Optional.empty();
        }

        // Check if session is expired
        if (session.getExpiresAt().isBefore(Instant.now())) {
            log.debug("Session {} is expired", session.getId());
            return Optional.empty();
        }

        return Optional.of(session);
    }

    /**
     * Creates a new user if one doesn't exist, or returns existing user.
     * 
     * For now, attaches all dev users to the FIRST tenant in DB.
     * This will be fixed later with a real onboarding flow.
     */
    @Transactional
    public User createUserIfMissing(String email, String displayName) {
        // Check if user already exists
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            log.debug("User already exists with email: {}", email);
            return existingUser.get();
        }

        // Get first tenant (dev mode - will be fixed later)
        List<Tenant> tenants = tenantRepository.findAll();
        if (tenants.isEmpty()) {
            throw new IllegalStateException("No tenants found in database. Please create a tenant first.");
        }

        Tenant tenant = tenants.get(0);
        log.debug("Creating new user for email: {} attached to tenant: {}", email, tenant.getId());

        // Create new user
        User user = User.builder()
                .email(email)
                .displayName(displayName)
                .tenant(tenant)
                .provider("DEV")
                .providerUserId(null)
                .createdAt(Instant.now())
                .build();

        User savedUser = userRepository.save(user);
        log.info("Created new user: {} for tenant: {}", savedUser.getId(), tenant.getId());

        return savedUser;
    }

    /**
     * Creates a new session for a user.
     */
    @Transactional
    public UserSession createSession(User user) {
        String sessionToken = generateSessionToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(SESSION_DURATION_DAYS * 24L * 60L * 60L); // 14 days

        UserSession session = UserSession.builder()
                .user(user)
                .sessionToken(sessionToken)
                .createdAt(now)
                .expiresAt(expiresAt)
                .invalidatedAt(null)
                .build();

        UserSession savedSession = userSessionRepository.save(session);
        log.info("Created session for user: {} with token prefix: {}", user.getId(), sessionToken.substring(0, 8));

        return savedSession;
    }

    /**
     * Handles Google OAuth login - creates or updates user, returns user entity.
     */
    @Transactional
    public User handleGoogleLogin(com.asp.auth.dto.GoogleUserInfo googleUserInfo) {
        // Check if email is verified
        if (!googleUserInfo.isEmailVerified()) {
            throw new IllegalArgumentException("Google email is not verified");
        }

        // Try to find existing user by email
        Optional<User> existingUserOpt = userRepository.findByEmail(googleUserInfo.getEmail());

        User user;
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            // Update providerUserId if missing
            if (user.getProviderUserId() == null || user.getProviderUserId().isEmpty()) {
                user.setProviderUserId(googleUserInfo.getGoogleUserId());
                user = userRepository.save(user);
                log.debug("Updated providerUserId for existing user: {}", user.getEmail());
            }
        } else {
            // Get first tenant (dev mode - will be fixed later with onboarding)
            List<Tenant> tenants = tenantRepository.findAll();
            if (tenants.isEmpty()) {
                throw new IllegalStateException("No tenants found in database. Please create a tenant first.");
            }

            Tenant tenant = tenants.get(0);
            log.debug("Creating new Google user for email: {} attached to tenant: {}", 
                    googleUserInfo.getEmail(), tenant.getId());

            // Create new user
            user = User.builder()
                    .email(googleUserInfo.getEmail())
                    .displayName(googleUserInfo.getDisplayName())
                    .tenant(tenant)
                    .provider("GOOGLE")
                    .providerUserId(googleUserInfo.getGoogleUserId())
                    .createdAt(Instant.now())
                    .build();

            user = userRepository.save(user);
            log.info("Created new Google user: {} for tenant: {}", user.getId(), tenant.getId());
        }

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        return user;
    }

    /**
     * Generates a secure random session token (64 characters, URL-safe Base64).
     */
    private String generateSessionToken() {
        byte[] randomBytes = new byte[48]; // 48 bytes = 64 Base64 characters
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}

