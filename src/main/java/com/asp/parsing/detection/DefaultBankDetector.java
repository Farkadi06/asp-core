package com.asp.parsing.detection;

import com.asp.common.Constants;
import com.asp.domain.Bank;
import com.asp.domain.BankConnection;
import com.asp.domain.BankConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
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
    // Note: "attijari" pattern includes "awb" as an alias
    private static final Map<String, Pattern> BANK_PATTERNS = Map.of(
        "cih", Pattern.compile("(?i)(cih|crédit immobilier et hôtelier)"),
        "attijari", Pattern.compile("(?i)(attijari|attijariwafa|attijari wafa|awb)"),
        "bmce", Pattern.compile("(?i)(bmce|banque marocaine du commerce extérieur)"),
        "bp", Pattern.compile("(?i)(banque populaire|banque populaire du maroc|groupe banque populaire|bp banque populaire|bp)")
    );

    @Override
    public BankDetectionResult detect(String extractedText, UUID bankConnectionId) {
        log.debug("{} Detecting bank from text (length: {}), bankConnectionId: {}", 
            Constants.LOG_PREFIX_PROCESS,
            extractedText != null ? extractedText.length() : 0, bankConnectionId);

        if (extractedText == null || extractedText.trim().isEmpty()) {
            log.warn("{} Empty extracted text provided, using fallback", Constants.LOG_PREFIX_PROCESS);
            return getFallbackResult(bankConnectionId);
        }

        // Step 1: Pattern-based detection
        PatternDetectionResult patternResult = detectFromPatterns(extractedText);

        // Step 2: If HIGH confidence, return pattern result
        if (patternResult != null && patternResult.getConfidence() == BankDetectionResult.DetectionConfidence.HIGH) {
            log.info("{} Pattern detection found HIGH confidence match: {} ({} matches)", 
                Constants.LOG_PREFIX_PROCESS, patternResult.getBankIdentifier(), patternResult.getMatchCount());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "pattern_matching");
            metadata.put("matchCount", patternResult.getMatchCount());
            metadata.put("matchesByBank", patternResult.getMatchesByBank());
            metadata.put("originalLength", extractedText.length());
            
            return BankDetectionResult.builder()
                .bankIdentifier(patternResult.getBankIdentifier())
                .confidence(patternResult.getConfidence())
                .metadata(metadata)
                .matchedPatterns(patternResult.getMatchedPatterns())
                .build();
        }

        // Step 3: Fallback to bank connection
        BankDetectionResult fallbackResult = getFallbackResult(bankConnectionId);
        
        // Step 4: Enrich fallback with pattern results if available
        Map<String, Object> enrichedMetadata = new HashMap<>();
        if (fallbackResult.getMetadata() != null) {
            enrichedMetadata.putAll(fallbackResult.getMetadata());
        }
        
        if (patternResult != null) {
            enrichedMetadata.put("pattern_bestBank", patternResult.getBankIdentifier());
            enrichedMetadata.put("pattern_confidence", patternResult.getConfidence().name());
            enrichedMetadata.put("pattern_matchCount", patternResult.getMatchCount());
            enrichedMetadata.put("pattern_matchesByBank", patternResult.getMatchesByBank());
            enrichedMetadata.put("pattern_matchedPatterns", patternResult.getMatchedPatterns());
            
            // Check for mismatch
            String patternBank = patternResult.getBankIdentifier();
            String fallbackBank = fallbackResult.getBankIdentifier();
            boolean connectionMismatch = patternBank != null 
                && fallbackBank != null 
                && !patternBank.equalsIgnoreCase(fallbackBank);
            
            if (connectionMismatch) {
                enrichedMetadata.put("connection_mismatch", true);
                log.warn("{} Bank detection mismatch: pattern detected '{}' but connection is '{}'", 
                    Constants.LOG_PREFIX_PROCESS, patternBank, fallbackBank);
            } else {
                enrichedMetadata.put("connection_mismatch", false);
            }
        } else {
            enrichedMetadata.put("pattern_bestBank", null);
            enrichedMetadata.put("pattern_confidence", "NONE");
            enrichedMetadata.put("connection_mismatch", false);
        }
        
        enrichedMetadata.put("originalLength", extractedText.length());
        
        return BankDetectionResult.builder()
            .bankIdentifier(fallbackResult.getBankIdentifier())
            .confidence(fallbackResult.getConfidence())
            .metadata(enrichedMetadata)
            .matchedPatterns(patternResult != null ? patternResult.getMatchedPatterns() : fallbackResult.getMatchedPatterns())
            .build();
    }

    /**
     * Detects bank from extracted text using pattern matching.
     *
     * @param extractedText the extracted PDF text
     * @return pattern detection result, or null if no matches found
     */
    private PatternDetectionResult detectFromPatterns(String extractedText) {
        Map<String, Integer> matchesByBank = new HashMap<>();
        Map<String, List<String>> matchedPatternsByBank = new HashMap<>();

        // Count matches for each bank pattern
        for (Map.Entry<String, Pattern> entry : BANK_PATTERNS.entrySet()) {
            String bankIdentifier = entry.getKey();
            Pattern pattern = entry.getValue();
            
            Matcher matcher = pattern.matcher(extractedText);
            int matchCount = 0;
            List<String> matchedPatterns = new ArrayList<>();
            
            while (matcher.find()) {
                matchCount++;
                String matchedText = matcher.group();
                if (!matchedPatterns.contains(matchedText)) {
                    matchedPatterns.add(matchedText);
                }
            }
            
            if (matchCount > 0) {
                matchesByBank.put(bankIdentifier, matchCount);
                matchedPatternsByBank.put(bankIdentifier, matchedPatterns);
                log.debug("{} Found {} matches for bank '{}': {}", 
                    Constants.LOG_PREFIX_PROCESS, matchCount, bankIdentifier, matchedPatterns);
            }
        }

        if (matchesByBank.isEmpty()) {
            log.debug("{} No pattern matches found in extracted text", Constants.LOG_PREFIX_PROCESS);
            return null;
        }

        // Find bank with highest match count
        String bestBank = matchesByBank.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        if (bestBank == null) {
            return null;
        }

        int bestMatchCount = matchesByBank.get(bestBank);
        BankDetectionResult.DetectionConfidence confidence = determineConfidence(bestMatchCount, extractedText.length());
        
        List<String> allMatchedPatterns = matchedPatternsByBank.getOrDefault(bestBank, Collections.emptyList());

        log.info("{} Pattern detection: best match = '{}' with {} matches (confidence: {})", 
            Constants.LOG_PREFIX_PROCESS, bestBank, bestMatchCount, confidence);

        return new PatternDetectionResult(
            bestBank,
            confidence,
            bestMatchCount,
            matchesByBank,
            allMatchedPatterns
        );
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

    /**
     * Internal helper class to hold pattern detection results.
     */
    private static class PatternDetectionResult {
        private final String bankIdentifier;
        private final BankDetectionResult.DetectionConfidence confidence;
        private final int matchCount;
        private final Map<String, Integer> matchesByBank;
        private final List<String> matchedPatterns;

        public PatternDetectionResult(String bankIdentifier, 
                                     BankDetectionResult.DetectionConfidence confidence,
                                     int matchCount,
                                     Map<String, Integer> matchesByBank,
                                     List<String> matchedPatterns) {
            this.bankIdentifier = bankIdentifier;
            this.confidence = confidence;
            this.matchCount = matchCount;
            this.matchesByBank = matchesByBank;
            this.matchedPatterns = matchedPatterns;
        }

        public String getBankIdentifier() {
            return bankIdentifier;
        }

        public BankDetectionResult.DetectionConfidence getConfidence() {
            return confidence;
        }

        public int getMatchCount() {
            return matchCount;
        }

        public Map<String, Integer> getMatchesByBank() {
            return matchesByBank;
        }

        public List<String> getMatchedPatterns() {
            return matchedPatterns;
        }
    }

    private BankDetectionResult getFallbackResult(UUID bankConnectionId) {
        if (bankConnectionId == null) {
            log.warn("{} No bank connection ID provided for fallback", Constants.LOG_PREFIX_PROCESS);
            return BankDetectionResult.builder()
                .bankIdentifier(null)
                .confidence(BankDetectionResult.DetectionConfidence.LOW)
                .metadata(Map.of("source", "fallback_no_connection"))
                .matchedPatterns(Collections.emptyList())
                .build();
        }

        // Try to get bank from connection
        BankConnection connection = bankConnectionRepository.findById(bankConnectionId).orElse(null);
        if (connection != null && connection.getBank() != null) {
            Bank bank = connection.getBank();
            log.info("{} Using fallback bank from connection: {} ({})", 
                Constants.LOG_PREFIX_PROCESS, bank.getSlug(), bank.getName());
            return BankDetectionResult.builder()
                .bankIdentifier(bank.getSlug())
                .confidence(BankDetectionResult.DetectionConfidence.LOW)
                .metadata(Map.of("source", "bank_connection_fallback"))
                .matchedPatterns(Collections.emptyList())
                .build();
        }

        log.warn("{} Could not find bank connection {} for fallback", 
            Constants.LOG_PREFIX_PROCESS, bankConnectionId);
        return BankDetectionResult.builder()
            .bankIdentifier(null)
            .confidence(BankDetectionResult.DetectionConfidence.LOW)
            .metadata(Map.of("source", "fallback_not_found"))
            .matchedPatterns(Collections.emptyList())
            .build();
    }
}

