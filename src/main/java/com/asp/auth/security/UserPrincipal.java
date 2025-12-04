package com.asp.auth.security;

import java.util.UUID;

/**
 * Principal object for authenticated dashboard users.
 * Used to populate Spring Security context for session-based authentication.
 */
public record UserPrincipal(
    UUID userId,
    UUID tenantId,
    String email
) {}

