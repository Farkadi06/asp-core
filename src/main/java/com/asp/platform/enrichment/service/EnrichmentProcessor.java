package com.asp.platform.enrichment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentProcessor {

    private final EnrichmentService enrichmentService;

    @Transactional
    public EnrichmentService.EnrichmentResult processIngestion(UUID ingestionId, UUID tenantId) {
        log.info("[ENRICHMENT][INGESTION:{}] Triggering enrichment for tenant {}", ingestionId, tenantId);
        EnrichmentService.EnrichmentResult result = enrichmentService.enrichIngestion(tenantId, ingestionId);
        log.info("[ENRICHMENT][INGESTION:{}] Enrichment finished. normalized={}, enriched={}",
            ingestionId, result.normalizedCount(), result.enrichedCount());
        return result;
    }
}

