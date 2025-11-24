package com.asp.parsing.detection;

import java.util.UUID;

/**
 * Interface for detecting which bank issued a statement.
 */
public interface BankDetector {
    /**
     * Detects the bank from extracted text.
     *
     * @param extractedText the extracted text from PDF
     * @param bankConnectionId the bank connection ID (for fallback)
     * @return the bank detection result
     */
    BankDetectionResult detect(String extractedText, UUID bankConnectionId);
}

