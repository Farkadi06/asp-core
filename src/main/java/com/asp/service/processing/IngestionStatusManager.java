package com.asp.service.processing;

import com.asp.common.Constants;
import com.asp.domain.Ingestion;
import com.asp.domain.IngestionRepository;
import com.asp.domain.IngestionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for managing ingestion status transitions.
 * Centralizes all status update logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionStatusManager {

    private final IngestionRepository ingestionRepository;

    /**
     * Updates the ingestion status.
     *
     * @param ingestion the ingestion entity
     * @param status the new status
     * @param errorCode optional error code
     * @param errorMessage optional error message
     */
    @Transactional
    public void updateStatus(Ingestion ingestion, IngestionStatus status, 
                            String errorCode, String errorMessage) {
        ingestion.setStatus(status);
        if (errorCode != null) {
            ingestion.setErrorCode(errorCode);
        }
        if (errorMessage != null) {
            ingestion.setErrorMessage(errorMessage);
        }
        ingestionRepository.save(ingestion);
        
        log.info("{}[{}] Status updated to {}", 
                Constants.LOG_PREFIX_INGESTION, ingestion.getId(), status);
    }

    /**
     * Marks the ingestion as PARSED and sets the processed_at timestamp.
     *
     * @param ingestion the ingestion entity
     */
    @Transactional
    public void markAsParsed(Ingestion ingestion) {
        ingestion.setStatus(IngestionStatus.PARSED);
        ingestion.setProcessedAt(Instant.now());
        ingestionRepository.save(ingestion);
        
        log.info("{} Updated ingestion status to PARSED", Constants.LOG_PREFIX_PROCESS);
    }

    /**
     * Marks the ingestion as FAILED with error details.
     *
     * @param ingestion the ingestion entity
     * @param errorCode the error code
     * @param errorMessage the error message
     */
    @Transactional
    public void markAsFailed(Ingestion ingestion, String errorCode, String errorMessage) {
        updateStatus(ingestion, IngestionStatus.FAILED, errorCode, errorMessage);
    }

    /**
     * Checks if the ingestion can be processed (is in PENDING or FAILED state).
     *
     * @param ingestion the ingestion entity
     * @return true if the ingestion can be processed
     */
    public boolean canProcess(Ingestion ingestion) {
        return ingestion.getStatus() == IngestionStatus.PENDING 
            || ingestion.getStatus() == IngestionStatus.FAILED;
    }
}

