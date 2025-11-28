package com.asp.controller.internal;

import com.asp.domain.Ingestion;
import com.asp.domain.IngestionRepository;
import com.asp.mapper.IngestionMetadataMapper;
import com.asp.shared.dto.IngestionMetadataDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Internal API controller for ingestion metadata.
 * Used by asp-worker to fetch ingestion details.
 */
@RestController
@RequestMapping("/internal/ingestions")
@RequiredArgsConstructor
@Slf4j
public class IngestionMetadataController {

    private final IngestionRepository ingestionRepository;
    private final IngestionMetadataMapper mapper;

    @Value("${ASP_INTERNAL_ACCESS_TOKEN:}")
    private String expectedToken;

    @GetMapping("/{ingestionId}")
    public ResponseEntity<IngestionMetadataDto> getIngestionMetadata(
            @PathVariable UUID ingestionId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {

        log.debug("[INTERNAL] Fetching ingestion metadata for ingestionId: {}", ingestionId);

        // Validate internal token
        if (!isValidToken(token)) {
            log.warn("[INTERNAL] Invalid or missing internal token for ingestionId: {}", ingestionId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }

        // Fetch ingestion from database
        Ingestion ingestion = ingestionRepository.findById(ingestionId)
            .orElseThrow(() -> {
                log.warn("[INTERNAL] Ingestion not found: {}", ingestionId);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion not found: " + ingestionId);
            });

        // Map to DTO
        IngestionMetadataDto dto = mapper.toDto(ingestion);

        log.debug("[INTERNAL] Successfully retrieved metadata for ingestionId: {}", ingestionId);
        return ResponseEntity.ok(dto);
    }

    private boolean isValidToken(String token) {
        if (expectedToken == null || expectedToken.trim().isEmpty()) {
            log.warn("[INTERNAL] ASP_INTERNAL_ACCESS_TOKEN not configured. Internal API access is disabled.");
            return false;
        }
        return expectedToken.equals(token);
    }
}

