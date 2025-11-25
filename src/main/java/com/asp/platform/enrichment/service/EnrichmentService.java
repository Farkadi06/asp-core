package com.asp.platform.enrichment.service;

import com.asp.domain.NormalizedTransaction;
import com.asp.domain.NormalizedTransactionRepository;
import com.asp.platform.enrichment.domain.EnrichedTransaction;
import com.asp.platform.enrichment.domain.EnrichedTransactionRepository;
import com.asp.platform.enrichment.mapper.EnrichedTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentService {

    private final NormalizedTransactionRepository normalizedTransactionRepository;
    private final EnrichedTransactionRepository enrichedTransactionRepository;
    private final EnrichedTransactionMapper enrichedTransactionMapper;

    @Transactional
    public EnrichmentResult enrichIngestion(UUID tenantId, UUID ingestionId) {
        log.info("[ENRICHMENT][INGESTION:{}][TENANT:{}] Loading normalized transactions", ingestionId, tenantId);
        List<NormalizedTransaction> normalizedTransactions = normalizedTransactionRepository.findAllByIngestionId(ingestionId);
        int normalizedCount = normalizedTransactions.size();
        log.info("[ENRICHMENT][INGESTION:{}] Found {} normalized transactions", ingestionId, normalizedCount);

        if (normalizedTransactions.isEmpty()) {
            return new EnrichmentResult(normalizedCount, 0);
        }

        enrichedTransactionRepository.deleteByNormalizedTransaction_Ingestion_Id(ingestionId);
        log.debug("[ENRICHMENT][INGESTION:{}] Cleared previous enriched rows", ingestionId);

        int enrichedCount = 0;
        for (NormalizedTransaction tx : normalizedTransactions) {
            try {
                EnrichedTransaction enriched = enrichedTransactionMapper.fromNormalizedTransaction(tx);
                enrichedTransactionRepository.save(enriched);
                enrichedCount++;
            } catch (Exception ex) {
                log.error("[ENRICHMENT][INGESTION:{}][TX:{}] Failed to enrich transaction", ingestionId,
                    tx.getTransactionId(), ex);
            }
        }

        log.info("[ENRICHMENT][INGESTION:{}] Enrichment completed. {} / {} rows persisted",
            ingestionId, enrichedCount, normalizedCount);

        return new EnrichmentResult(normalizedCount, enrichedCount);
    }

    public record EnrichmentResult(int normalizedCount, int enrichedCount) {}
}

