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

    @Transactional
    public CreateApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        // Validate tenant exists and is ACTIVE
        Tenant tenant = tenantRepository.findById(request.getTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + request.getTenantId()));

        if (tenant.getStatus() != Tenant.TenantStatus.ACTIVE) {
            throw new IllegalStateException("Tenant is not ACTIVE: " + request.getTenantId());
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
        String fullKey = request.getSandbox() 
            ? "asp_sandbox_" + prefix + "_" + secret
            : "asp_live_" + prefix + "_" + secret;

        // Hash the secret
        String keyHash = passwordEncoder.encode(secret);

        // Create and save API key
        ApiKey apiKey = ApiKey.builder()
            .tenant(tenant)
            .prefix(prefix)
            .keyHash(keyHash)
            .displayName(request.getDisplayName())
            .sandbox(request.getSandbox())
            .scopes(request.getScopes())
            .build();

        ApiKey savedApiKey = apiKeyRepository.save(apiKey);

        // Log the creation
        ApiKeyActionLogger.logCreate(tenant.getId(), savedApiKey.getId(), prefix, request.getScopes());

        log.info("Created API key with prefix {} for tenant {}", prefix, tenant.getId());

        // Return response with full key (shown only once)
        return CreateApiKeyResponse.builder()
            .id(savedApiKey.getId())
            .prefix(prefix)
            .apiKey(fullKey)
            .build();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID tenantId) {
        List<ApiKey> apiKeys = apiKeyRepository.findByTenantId(tenantId);

        return apiKeys.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteApiKey(UUID apiKeyId, UUID tenantId) {
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

