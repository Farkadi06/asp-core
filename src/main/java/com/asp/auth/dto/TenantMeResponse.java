package com.asp.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for /internal/tenant/me endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMeResponse {
    private UUID id;
    private String name;
    private String slug;
    private String status;
    private String plan;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

