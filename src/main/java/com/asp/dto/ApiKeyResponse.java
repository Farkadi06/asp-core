package com.asp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyResponse {
    private UUID id;
    private String displayName;
    private String prefix;
    private Set<String> scopes;
    private Boolean sandbox;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastUsedAt;
}

