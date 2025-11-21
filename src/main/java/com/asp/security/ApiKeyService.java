package com.asp.security;

import com.asp.domain.ApiKey;
import com.asp.domain.ApiKeyRepository;
import com.asp.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Optional<ApiKeyValidationResult> validateApiKey(String prefix, String secret) {
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByPrefix(prefix);

        if (apiKeyOpt.isEmpty()) {
            log.debug("API key not found for prefix: {}", prefix);
            return Optional.empty();
        }

        ApiKey apiKey = apiKeyOpt.get();

        // Validate secret
        if (!passwordEncoder.matches(secret, apiKey.getKeyHash())) {
            log.debug("Invalid secret for API key prefix: {}", prefix);
            return Optional.empty();
        }

        Tenant tenant = apiKey.getTenant();

        // Check tenant status
        if (tenant.getStatus() != Tenant.TenantStatus.ACTIVE) {
            log.debug("Tenant {} is not ACTIVE (status: {})", tenant.getId(), tenant.getStatus());
            return Optional.empty();
        }

        // Update last used timestamp
        apiKey.setLastUsedAt(OffsetDateTime.now());
        apiKeyRepository.save(apiKey);

        return Optional.of(new ApiKeyValidationResult(
            tenant.getId(),
            apiKey.getSandbox(),
            apiKey.getScopes()
        ));
    }

    public record ApiKeyValidationResult(
        UUID tenantId,
        boolean sandbox,
        Set<String> scopes
    ) {}
}

