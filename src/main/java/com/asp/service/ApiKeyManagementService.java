package com.asp.service;

import com.asp.domain.ApiKey;
import com.asp.domain.ApiKeyRepository;
import com.asp.domain.Tenant;
import com.asp.domain.TenantRepository;
import com.asp.dto.ApiKeyResponse;
import com.asp.dto.CreateApiKeyRequest;
import com.asp.dto.CreateApiKeyResponse;
import com.asp.util.ApiKeyActionLogger;
import com.asp.util.RandomStringGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyManagementService {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int PREFIX_LENGTH = 8;
    private static final int SECRET_LENGTH = 32;

    /**
     * Public API entry point for creating API keys.
     * 
     * NOTE: This method is deprecated. Use createApiKeyForTenant() directly.
     * Tenant ID should be resolved from the API key (via TenantContext), not from request body.
     * 
     * @deprecated Use createApiKeyForTenant() with tenantId from TenantContext
     */
    @Deprecated
    @Transactional
    public CreateApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        // This method is kept for backward compatibility but should not be used.
        // Public API controller should call createApiKeyForTenant() directly with tenantId from TenantContext.
        return createApiKeyForTenant(
                request.getTenantId(),  // This will be null due to @JsonIgnore, but kept for compatibility
                request.getDisplayName(),
                request.getScopes(),
                request.getSandbox() != null ? request.getSandbox() : false
        );
    }

    /**
     * Creates an API key for a tenant.
     * This method separates "who is the tenant?" from "how it's passed".
     * 
     * @param tenantId The tenant ID (resolved from API key or session)
     * @param displayName Human-readable name for the key
     * @param scopes Permissions for the key
     * @param sandbox Whether this is a sandbox key
     * @return CreateApiKeyResponse with the full API key (shown only once)
     */
    @Transactional
    public CreateApiKeyResponse createApiKeyForTenant(UUID tenantId, String displayName, java.util.Set<String> scopes, boolean sandbox) {
        // Validate tenant exists and is ACTIVE
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        if (tenant.getStatus() != Tenant.TenantStatus.ACTIVE) {
            throw new IllegalStateException("Tenant is not ACTIVE: " + tenantId);
        }

        // Generate prefix (ensure uniqueness)
        String prefix;
        int attempts = 0;
        do {
            prefix = RandomStringGenerator.generateAlphanumeric(PREFIX_LENGTH);
            attempts++;
            if (attempts > 10) {
                throw new RuntimeException("Failed to generate unique prefix after 10 attempts");
            }
        } while (apiKeyRepository.existsByPrefix(prefix));

        // Generate secret
        String secret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);

        // Create full key
        String fullKey = sandbox 
            ? "asp_sandbox_" + prefix + "_" + secret
            : "asp_live_" + prefix + "_" + secret;

        // Hash the secret
        String keyHash = passwordEncoder.encode(secret);

        // Create and save API key
        ApiKey apiKey = ApiKey.builder()
            .tenant(tenant)
            .prefix(prefix)
            .keyHash(keyHash)
            .displayName(displayName)
            .sandbox(sandbox)
            .scopes(scopes)
            .build();

        ApiKey savedApiKey = apiKeyRepository.save(apiKey);

        // Log the creation
        ApiKeyActionLogger.logCreate(tenant.getId(), savedApiKey.getId(), prefix, scopes);

        log.info("Created API key with prefix {} for tenant {}", prefix, tenant.getId());

        // Return response with full key (shown only once)
        return CreateApiKeyResponse.builder()
            .id(savedApiKey.getId())
            .prefix(prefix)
            .apiKey(fullKey)
            .build();
    }

    /**
     * Lists all API keys for a tenant.
     * 
     * @param tenantId The tenant ID (resolved from API key or session)
     * @return List of API key responses (without full key secret)
     */
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeysForTenant(UUID tenantId) {
        List<ApiKey> apiKeys = apiKeyRepository.findByTenantId(tenantId);

        return apiKeys.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Lists all API keys for a tenant (public API endpoint).
     * Tenant ID comes from TenantContext (set by API key authentication).
     * 
     * @deprecated Use listApiKeysForTenant() directly
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID tenantId) {
        return listApiKeysForTenant(tenantId);
    }

    /**
     * Deletes an API key for a tenant.
     * 
     * @param tenantId The tenant ID (resolved from API key or session)
     * @param apiKeyId The API key ID to delete
     */
    @Transactional
    public void deleteApiKeyForTenant(UUID tenantId, UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow(() -> new IllegalArgumentException("API key not found: " + apiKeyId));

        // Verify it belongs to the tenant
        if (!apiKey.getTenant().getId().equals(tenantId)) {
            throw new IllegalStateException("API key does not belong to tenant: " + tenantId);
        }

        // Hard delete (scopes will be deleted via CASCADE)
        apiKeyRepository.delete(apiKey);

        // Log the deletion
        ApiKeyActionLogger.logDelete(apiKeyId);

        log.info("Deleted API key {} for tenant {}", apiKeyId, tenantId);
    }

    /**
     * Deletes an API key (public API endpoint).
     * Tenant ID comes from TenantContext (set by API key authentication).
     * 
     * @deprecated Use deleteApiKeyForTenant() directly
     */
    @Deprecated
    @Transactional
    public void deleteApiKey(UUID apiKeyId, UUID tenantId) {
        deleteApiKeyForTenant(tenantId, apiKeyId);
    }

    private ApiKeyResponse mapToResponse(ApiKey apiKey) {
        return ApiKeyResponse.builder()
            .id(apiKey.getId())
            .displayName(apiKey.getDisplayName())
            .prefix(apiKey.getPrefix())
            .scopes(apiKey.getScopes())
            .sandbox(apiKey.getSandbox())
            .createdAt(apiKey.getCreatedAt())
            .lastUsedAt(apiKey.getLastUsedAt())
            .build();
    }
}

