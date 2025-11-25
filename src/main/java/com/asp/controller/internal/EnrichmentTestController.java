package com.asp.controller.internal;

import com.asp.platform.enrichment.service.EnrichmentProcessor;
import com.asp.platform.enrichment.service.EnrichmentService;
import com.asp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/test")
@RequiredArgsConstructor
@Slf4j
public class EnrichmentTestController {

    private final EnrichmentProcessor enrichmentProcessor;

    @PostMapping("/run-enrichment/{ingestionId}")
    public ResponseEntity<Map<String, Object>> runEnrichment(@PathVariable UUID ingestionId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.warn("[ENRICHMENT][TEST] Tenant context not available");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context not available");
        }

        log.info("[ENRICHMENT][TEST] Manually triggering enrichment for ingestion {}", ingestionId);
        EnrichmentService.EnrichmentResult result = enrichmentProcessor.processIngestion(ingestionId, tenantId);

        Map<String, Object> body = new HashMap<>();
        body.put("ingestionId", ingestionId);
        body.put("normalizedCount", result.normalizedCount());
        body.put("enrichedCount", result.enrichedCount());

        return ResponseEntity.ok(body);
    }
}

