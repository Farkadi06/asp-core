package com.asp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for public API key creation (via /v1/api-keys).
 * 
 * NOTE: tenantId is derived from the authenticated API key (X-Api-Key header).
 * The tenantId field is deprecated and will be removed in a future release.
 * 
 * TODO: Remove tenantId in v2 of the API
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApiKeyRequest {

    /**
     * @deprecated Tenant ID is now derived from the authenticated API key.
     * This field is ignored in the controller and will be removed in v2 of the API.
     * The tenantId is always resolved from the X-Api-Key header via TenantContext.
     */
    @Deprecated
    private UUID tenantId;

    @NotBlank(message = "displayName is required")
    private String displayName;

    @NotEmpty(message = "scopes cannot be empty")
    private Set<String> scopes;

    @Builder.Default
    private Boolean sandbox = false;
}

