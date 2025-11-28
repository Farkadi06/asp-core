package com.asp.service.processing;

import com.asp.common.Constants;
import com.asp.parsing.processor.IngestionProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Facade for ingestion processing operations.
 * Provides a clean interface for controllers to interact with the processing pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionProcessingFacade {

    private final IngestionProcessorService ingestionProcessorService;

    /**
     * Triggers processing for a single ingestion.
     *
     * @param ingestionId the ingestion ID
     */
    public void processIngestion(UUID ingestionId) {
        log.info("{} Triggering processing for ingestion: {}", Constants.LOG_PREFIX_PROCESS, ingestionId);
        ingestionProcessorService.processIngestion(ingestionId);
        log.info("{} Processing triggered successfully for ingestion: {}", Constants.LOG_PREFIX_PROCESS, ingestionId);
    }

    /**
     * Retries processing for a failed ingestion.
     *
     * @param ingestionId the ingestion ID
     */
    public void retryFailedIngestion(UUID ingestionId) {
        log.info("{} Retrying failed ingestion: {}", Constants.LOG_PREFIX_PROCESS, ingestionId);
        ingestionProcessorService.retryFailedIngestion(ingestionId);
    }
}

