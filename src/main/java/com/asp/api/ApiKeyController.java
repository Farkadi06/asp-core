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

@RestController
@RequestMapping("/v1/api-keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyController {

    private final ApiKeyManagementService apiKeyManagementService;

    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        log.debug("Creating API key for tenant: {}", request.getTenantId());
        CreateApiKeyResponse response = apiKeyManagementService.createApiKey(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys() {
        UUID tenantId = TenantContext.getTenantId();
        
        if (tenantId == null) {
            log.warn("Tenant context not set for API key listing");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Listing API keys for tenant: {}", tenantId);
        List<ApiKeyResponse> apiKeys = apiKeyManagementService.listApiKeys(tenantId);
        return ResponseEntity.ok(apiKeys);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<DeleteApiKeyResponse> deleteApiKey(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        
        if (tenantId == null) {
            log.warn("Tenant context not set for API key deletion");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Deleting API key {} for tenant: {}", id, tenantId);
        apiKeyManagementService.deleteApiKey(id, tenantId);
        return ResponseEntity.ok(DeleteApiKeyResponse.builder().deleted(true).build());
    }
}

