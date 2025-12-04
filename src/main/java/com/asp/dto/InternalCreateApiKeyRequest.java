package com.asp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request DTO for internal API key creation (session-based).
 * Does NOT include tenantId - it's taken from the session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalCreateApiKeyRequest {

    @NotBlank(message = "displayName is required")
    private String displayName;

    @NotEmpty(message = "scopes cannot be empty")
    private Set<String> scopes;

    @Builder.Default
    private Boolean sandbox = false;
}

