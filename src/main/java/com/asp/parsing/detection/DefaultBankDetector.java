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

        if (extractedText == null || extractedText.trim().isEmpty()) {
            log.warn("Empty text provided for bank detection, using fallback");
            return getFallbackResult(bankConnectionId);
        }

        String text = extractedText.toLowerCase();
        Map<String, Integer> matches = new HashMap<>();
        List<String> matchedPatterns = new ArrayList<>();

        // Check each bank pattern
        for (Map.Entry<String, Pattern> entry : BANK_PATTERNS.entrySet()) {
            Pattern pattern = entry.getValue();
            java.util.regex.Matcher matcher = pattern.matcher(text);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            if (count > 0) {
                matches.put(entry.getKey(), count);
                matchedPatterns.add(entry.getKey() + " (matches: " + count + ")");
            }
        }

        // Determine best match
        if (matches.isEmpty()) {
            log.warn("No bank patterns matched, using fallback");
            return getFallbackResult(bankConnectionId);
        }

        // Find bank with most matches
        String detectedBank = matches.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        if (detectedBank == null) {
            return getFallbackResult(bankConnectionId);
        }

        int matchCount = matches.get(detectedBank);
        BankDetectionResult.DetectionConfidence confidence = determineConfidence(matchCount, text.length());

        log.info("Detected bank: {} with confidence: {} (matches: {})", 
            detectedBank, confidence, matchCount);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("matchCount", matchCount);
        metadata.put("allMatches", matches);

        return BankDetectionResult.builder()
            .bankIdentifier(detectedBank)
            .confidence(confidence)
            .metadata(metadata)
            .matchedPatterns(matchedPatterns)
            .build();
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

