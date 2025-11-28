package com.asp.service.processing;

import com.asp.common.Constants;
import com.asp.domain.Ingestion;
import com.asp.parsing.detection.BankDetector;
import com.asp.parsing.detection.BankDetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for detecting the bank from extracted text.
 * Wraps BankDetector to provide a clean interface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankDetectionService {

    private final BankDetector bankDetector;

    /**
     * Detects the bank from the extracted text.
     *
     * @param ingestion the ingestion entity
     * @param extractedText the extracted PDF text
     * @return the bank detection result
     */
    public BankDetectionResult detectBank(Ingestion ingestion, String extractedText) {
        log.info("{}[{}] Detecting bank from extracted text", 
                Constants.LOG_PREFIX_INGESTION, ingestion.getId());

        try {
            BankDetectionResult result = bankDetector.detect(extractedText, ingestion.getBankConnection().getId());
            
            log.info("{} Bank detected = {} (confidence: {})",
                    Constants.LOG_PREFIX_PROCESS, result.getBankIdentifier(), result.getConfidence());

            return result;
        } catch (Exception e) {
            log.error("{}[{}] Bank detection error: {}", 
                    Constants.LOG_PREFIX_INGESTION, ingestion.getId(), e.getMessage(), e);
            throw new RuntimeException("Bank detection failed: " + e.getMessage(), e);
        }
    }
}

