package com.asp.parsing.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Context information for parsing an ingestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionContext {
    private UUID ingestionId;
    private UUID tenantId;
    private UUID bankConnectionId;
    private String userRef;
    private String bankIdentifier;  // Bank slug
}

