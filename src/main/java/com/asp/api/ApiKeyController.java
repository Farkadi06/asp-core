// PUBLIC API — Authentication: X-Api-Key (no session)
// DO NOT use UserPrincipal or session-based methods here.

package com.asp.api;

import com.asp.dto.ApiKeyResponse;
import com.asp.dto.CreateApiKeyRequest;
import com.asp.dto.CreateApiKeyResponse;
import com.asp.dto.DeleteApiKeyResponse;
import com.asp.service.ApiKeyManagementService;
import com.asp.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Public API controller for API key management.
 * 
 * Authentication: X-Api-Key header (via ApiKeyAuthenticationFilter)
 * Tenant Resolution: Derived from the authenticated API key (TenantContext)
 * 
 * NOTE: tenantId in request body is deprecated and ignored.
 * Tenant ID is always derived from the authenticated API key.
 */
@RestController
@RequestMapping("/v1/api-keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyController {

    private final ApiKeyManagementService apiKeyManagementService;

    /**
     * Creates a new API key for the authenticated tenant.
     * 
     * NOTE: tenantId is derived from the authenticated API key (X-Api-Key header).
     * The tenantId field in the request body is deprecated and will be removed in a future release.
     */
    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        
        if (tenantId == null) {
            log.warn("Tenant context not set for API key creation - API key authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Warn if deprecated tenantId is provided in body
        if (request.getTenantId() != null) {
            log.warn("tenantId provided in request body is deprecated and ignored. Using tenantId from authenticated API key: {}", tenantId);
        }

        log.info("Public API Key request via X-Api-Key for tenant: {}", tenantId);
        CreateApiKeyResponse response = apiKeyManagementService.createApiKeyForTenant(
                tenantId,  // Always use tenantId from API key, ignore request.getTenantId()
                request.getDisplayName(),
                request.getScopes(),
                request.getSandbox() != null ? request.getSandbox() : false
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all API keys for the authenticated tenant.
     */
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys() {
        UUID tenantId = TenantContext.getTenantId();
        
        if (tenantId == null) {
            log.warn("Tenant context not set for API key listing - API key authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Public API Key request via X-Api-Key - listing keys for tenant: {}", tenantId);
        List<ApiKeyResponse> apiKeys = apiKeyManagementService.listApiKeysForTenant(tenantId);
        return ResponseEntity.ok(apiKeys);
    }

    /**
     * Deletes an API key for the authenticated tenant.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<DeleteApiKeyResponse> deleteApiKey(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        
        if (tenantId == null) {
            log.warn("Tenant context not set for API key deletion - API key authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Public API Key request via X-Api-Key - deleting key {} for tenant: {}", id, tenantId);
        apiKeyManagementService.deleteApiKeyForTenant(tenantId, id);
        return ResponseEntity.ok(DeleteApiKeyResponse.builder().deleted(true).build());
    }
}

