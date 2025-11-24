package com.asp.parsing.detection;

import com.asp.domain.Bank;
import com.asp.domain.BankConnection;
import com.asp.domain.BankConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Default implementation of bank detector using pattern matching.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultBankDetector implements BankDetector {

    private final BankConnectionRepository bankConnectionRepository;

    // Bank name patterns (case-insensitive)
    private static final Map<String, Pattern> BANK_PATTERNS = Map.of(
        "cih", Pattern.compile("(?i)(cih|crédit immobilier et hôtelier)"),
        "attijari", Pattern.compile("(?i)(attijari|attijariwafa|attijari wafa)"),
        "bmce", Pattern.compile("(?i)(bmce|banque marocaine du commerce extérieur)")
    );

    @Override
    public BankDetectionResult detect(String extractedText, UUID bankConnectionId) {
        log.debug("Detecting bank from text (length: {}), bankConnectionId: {}", 
            extractedText != null ? extractedText.length() : 0, bankConnectionId);

        // TEMPORARY: Hardcode CIH for debugging
        log.info("TEMPORARY: Hardcoding bank detection to 'cih' for debugging");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "hardcoded_debug");
        metadata.put("originalLength", extractedText != null ? extractedText.length() : 0);
        
        return BankDetectionResult.builder()
            .bankIdentifier("cih")
            .confidence(BankDetectionResult.DetectionConfidence.HIGH)
            .metadata(metadata)
            .matchedPatterns(List.of("cih (hardcoded)"))
            .build();

        // Original detection logic will be restored later:
        // - Pattern matching against BANK_PATTERNS
        // - Fallback to bank connection lookup
        // - Confidence calculation based on match count
    }

    private BankDetectionResult.DetectionConfidence determineConfidence(int matchCount, int textLength) {
        // Simple heuristic: more matches = higher confidence
        if (matchCount >= 3) {
            return BankDetectionResult.DetectionConfidence.HIGH;
        } else if (matchCount >= 2) {
            return BankDetectionResult.DetectionConfidence.MEDIUM;
        } else {
            return BankDetectionResult.DetectionConfidence.LOW;
        }
    }

    private BankDetectionResult getFallbackResult(UUID bankConnectionId) {
        if (bankConnectionId == null) {
            log.warn("No bank connection ID provided for fallback");
            return BankDetectionResult.builder()
                .bankIdentifier(null)
                .confidence(BankDetectionResult.DetectionConfidence.LOW)
                .build();
        }

        // Try to get bank from connection
        BankConnection connection = bankConnectionRepository.findById(bankConnectionId).orElse(null);
        if (connection != null && connection.getBank() != null) {
            Bank bank = connection.getBank();
            log.info("Using fallback bank from connection: {} ({})", bank.getSlug(), bank.getName());
            return BankDetectionResult.builder()
                .bankIdentifier(bank.getSlug())
                .confidence(BankDetectionResult.DetectionConfidence.LOW)
                .metadata(Map.of("source", "bank_connection_fallback"))
                .build();
        }

        log.warn("Could not find bank connection {} for fallback", bankConnectionId);
        return BankDetectionResult.builder()
            .bankIdentifier(null)
            .confidence(BankDetectionResult.DetectionConfidence.LOW)
            .build();
    }
}

