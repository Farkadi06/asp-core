// INTERNAL DASHBOARD API — Authentication: Session cookie (asp_session)
// NOT accessible via public API and NOT available to external clients.

package com.asp.api;

import com.asp.dto.ApiKeyResponse;
import com.asp.dto.CreateApiKeyResponse;
import com.asp.dto.DeleteApiKeyResponse;
import com.asp.dto.InternalCreateApiKeyRequest;
import com.asp.service.ApiKeyManagementService;
import com.asp.auth.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Internal API for dashboard use only.
 * 
 * Uses session-based authentication (UserPrincipal).
 * Never exposed to public API consumers.
 * Never uses X-Api-Key authentication.
 * 
 * These endpoints:
 * - Do NOT require X-Api-Key header
 * - Do NOT accept tenantId in the body
 * - Always use tenantId from the logged-in user's session (UserPrincipal)
 */
@RestController
@RequestMapping("/internal/api-keys")
@RequiredArgsConstructor
@Slf4j
public class InternalApiKeyController {

    private final ApiKeyManagementService apiKeyManagementService;

    /**
     * Lists all API keys for the authenticated tenant.
     */
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys() {
        UUID tenantId = getTenantIdFromSession();
        
        log.info("Internal API Key request for tenant: {} (session-based)", tenantId);
        List<ApiKeyResponse> apiKeys = apiKeyManagementService.listApiKeysForTenant(tenantId);
        return ResponseEntity.ok(apiKeys);
    }

    /**
     * Creates a new API key for the authenticated tenant.
     * Tenant ID is derived from the session (UserPrincipal), never from request body.
     */
    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> createApiKey(@Valid @RequestBody InternalCreateApiKeyRequest request) {
        UUID tenantId = getTenantIdFromSession();
        
        log.info("Internal API Key request for tenant: {} (session-based)", tenantId);
        CreateApiKeyResponse response = apiKeyManagementService.createApiKeyForTenant(
                tenantId,
                request.getDisplayName(),
                request.getScopes(),
                request.getSandbox() != null ? request.getSandbox() : false
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Deletes an API key for the authenticated tenant.
     * Only deletes if the key belongs to this tenant.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<DeleteApiKeyResponse> deleteApiKey(@PathVariable UUID id) {
        UUID tenantId = getTenantIdFromSession();
        
        log.info("Internal API Key request for tenant: {} (session-based) - deleting key: {}", tenantId, id);
        apiKeyManagementService.deleteApiKeyForTenant(tenantId, id);
        return ResponseEntity.ok(DeleteApiKeyResponse.builder().deleted(true).build());
    }

    /**
     * Extracts tenant ID from the authenticated session (UserPrincipal).
     */
    private UUID getTenantIdFromSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new IllegalStateException("User not authenticated - session required");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return principal.tenantId();
    }
}

