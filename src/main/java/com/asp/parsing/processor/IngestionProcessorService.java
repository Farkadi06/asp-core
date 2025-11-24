package com.asp.parsing.processor;

import java.util.UUID;

/**
 * Service for processing ingestions through the parsing pipeline.
 */
public interface IngestionProcessorService {
    /**
     * Processes all pending ingestions.
     */
    void processPendingIngestions();

    /**
     * Processes a specific ingestion.
     *
     * @param ingestionId the ingestion ID
     */
    void processIngestion(UUID ingestionId);

    /**
     * Retries a failed ingestion.
     *
     * @param ingestionId the ingestion ID
     */
    void retryFailedIngestion(UUID ingestionId);
}

