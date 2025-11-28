package com.asp.controller.internal;

import com.asp.parsing.processor.IngestionProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/ingestions")
@RequiredArgsConstructor
@Slf4j
public class IngestionProcessingController {

    private final IngestionProcessorService ingestionProcessorService;

    @Value("${ASP_INTERNAL_ACCESS_TOKEN}")
    private String internalAccessToken;

    @PostMapping("/{ingestionId}/process")
    public ResponseEntity<Map<String, String>> processIngestion(
            @PathVariable UUID ingestionId,
            @RequestHeader("X-Internal-Token") String xInternalToken) {

        // Validate internal token
        if (!internalAccessToken.equals(xInternalToken)) {
            log.warn("Unauthorized access attempt to /internal/ingestions/{}/process with invalid token", ingestionId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal access token");
        }

        log.info("[PROCESS] Triggering processing for ingestion: {}", ingestionId);

        try {
            // Process ingestion asynchronously (or synchronously - depends on implementation)
            ingestionProcessorService.processIngestion(ingestionId);

            Map<String, String> response = new HashMap<>();
            response.put("status", "queued");
            response.put("ingestionId", ingestionId.toString());

            log.info("[PROCESS] Processing triggered successfully for ingestion: {}", ingestionId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("[PROCESS] Ingestion not found: {}", ingestionId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion not found");
        } catch (Exception e) {
            log.error("[PROCESS] Error triggering processing for ingestion: {}", ingestionId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to trigger processing");
        }
    }
}
