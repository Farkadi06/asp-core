package com.asp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionResponse {
    private UUID id;
    private UUID bankConnectionId;
    private String userRef;
    private String status;
    private String source;
    private String ingestionType;
    private String originalFileName;
    private Long fileSize;
    private String storagePath;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant processedAt;
}

