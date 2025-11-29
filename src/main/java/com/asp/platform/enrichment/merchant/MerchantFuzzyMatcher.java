package com.asp.platform.enrichment.merchant;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Fuzzy matching engine for merchant names.
 * Uses Jaro-Winkler and Levenshtein distance to find similar merchant names.
 */
@Component
@Slf4j
public class MerchantFuzzyMatcher {

    private static final String RULES_PATH = "enrichment/merchant_rules.yml";
    private static final Pattern NON_ALPHANUM_UNDERSCORE = Pattern.compile("[^a-z0-9_]");

    private final Yaml yaml = new Yaml();
    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    private final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

    private Set<String> canonicalMerchants = new HashSet<>();

    @PostConstruct
    public void loadCanonicalMerchants() {
        try {
            ClassPathResource resource = new ClassPathResource(RULES_PATH);
            if (!resource.exists()) {
                log.warn("Merchant rules file not found at {}, fuzzy matching will use fallback merchants", RULES_PATH);
                loadFallbackMerchants();
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                Object loaded = yaml.load(inputStream);
                if (loaded instanceof Map<?, ?> map) {
                    Object aliasesObj = map.get("merchant_aliases");
                    if (aliasesObj instanceof Map<?, ?> aliasMap) {
                        Set<String> merchants = new HashSet<>();
                        for (Map.Entry<?, ?> entry : aliasMap.entrySet()) {
                            String key = entry.getKey() != null ? entry.getKey().toString() : null;
                            if (StringUtils.hasText(key)) {
                                merchants.add(key);
                            }
                        }
                        canonicalMerchants = merchants;
                        log.info("Loaded {} canonical merchants for fuzzy matching", canonicalMerchants.size());
                    }
                }
            }
        } catch (IOException ex) {
            log.error("Unable to read merchant rules from {}, using fallback merchants", RULES_PATH, ex);
            loadFallbackMerchants();
        }

        // Add fallback merchants if set is still empty
        if (canonicalMerchants.isEmpty()) {
            loadFallbackMerchants();
        }
    }

    private void loadFallbackMerchants() {
        canonicalMerchants = new HashSet<>(List.of(
            "marjane", "carrefour", "bim", "labelvie", "marwa",
            "decathlon", "planet_sport", "inwi", "orange", "iam",
            "uber", "uber_eats", "glovo", "jibi", "macdonalds",
            "kfc", "boost_coffee", "starbucks", "oxxo", "afriquia",
            "winxo", "shell", "total", "petrom", "bp_gaz",
            "netflix", "spotify", "youtube", "apple", "google",
            "microsoft", "adobe", "amazon", "shein", "jumia",
            "aliexpress", "ikea", "pharmacie", "fnac", "darty"
        ));
        log.info("Loaded {} fallback canonical merchants", canonicalMerchants.size());
    }

    /**
     * Match input string against canonical merchants using fuzzy matching.
     * 
     * @param input Normalized merchant name (lowercase, underscores, no accents)
     * @return MerchantMatch with best candidate, or empty if no good match found
     */
    public Optional<MerchantMatch> match(String input) {
        if (!StringUtils.hasText(input)) {
            return Optional.empty();
        }

        log.debug("[MERCHANT][FUZZY] Trying fuzzy match for: \"{}\"", input);

        String normalizedInput = normalizeForFuzzy(input);
        if (normalizedInput.isEmpty()) {
            return Optional.empty();
        }

        MerchantMatch bestMatch = null;
        double bestScore = 0.0;

        for (String candidate : canonicalMerchants) {
            String normalizedCandidate = normalizeForFuzzy(candidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }

            double score = computeSimilarityScore(normalizedInput, normalizedCandidate);
            
            if (score > bestScore) {
                bestScore = score;
                bestMatch = MerchantMatch.builder()
                    .canonical(candidate)
                    .score(bestScore)
                    .confidence(determineConfidence(bestScore))
                    .build();
            }
        }

        if (bestMatch != null && bestMatch.getConfidence() != MatchConfidence.NONE) {
            log.debug("[MERCHANT][FUZZY] Best candidate: \"{}\", score={:.3f}, confidence={}",
                bestMatch.getCanonical(), String.format("%.3f", bestMatch.getScore()), bestMatch.getConfidence());
            
            // Only return HIGH or MEDIUM matches
            if (bestMatch.getConfidence() == MatchConfidence.HIGH || 
                bestMatch.getConfidence() == MatchConfidence.MEDIUM) {
                return Optional.of(bestMatch);
            }
        }

        log.debug("[MERCHANT][FUZZY] No suitable match found (best score: {:.3f})", String.format("%.3f", bestScore));
        return Optional.empty();
    }

    /**
     * Normalize string for fuzzy matching comparison.
     */
    private String normalizeForFuzzy(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        
        // Lowercase and normalize Unicode
        String normalized = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        
        // Replace spaces with underscores
        normalized = normalized.replace(' ', '_');
        
        // Remove non-alphanumeric (keep underscores)
        normalized = NON_ALPHANUM_UNDERSCORE.matcher(normalized).replaceAll("");
        
        // Normalize multiple underscores
        normalized = normalized.replaceAll("_{2,}", "_");
        
        return normalized.trim();
    }

    /**
     * Compute similarity score using Jaro-Winkler and Levenshtein distance.
     * Returns average of both scores.
     */
    private double computeSimilarityScore(String input, String candidate) {
        // Jaro-Winkler similarity (0.0 to 1.0, higher is better)
        double jaroWinklerScore = jaroWinkler.apply(input, candidate);

        // Levenshtein distance (normalized to 0.0 to 1.0, higher is better)
        int maxLength = Math.max(input.length(), candidate.length());
        if (maxLength == 0) {
            return 1.0;
        }
        int levenshteinDistance = levenshtein.apply(input, candidate);
        double levenshteinScore = 1.0 - ((double) levenshteinDistance / maxLength);

        // Average of both scores
        return (jaroWinklerScore + levenshteinScore) / 2.0;
    }

    /**
     * Determine confidence level based on score.
     */
    private MatchConfidence determineConfidence(double score) {
        if (score >= 0.90) {
            return MatchConfidence.HIGH;
        } else if (score >= 0.80) {
            return MatchConfidence.MEDIUM;
        } else if (score >= 0.70) {
            return MatchConfidence.LOW;
        } else {
            return MatchConfidence.NONE;
        }
    }

    /**
     * Result of fuzzy matching operation.
     */
    @lombok.Value
    @lombok.Builder
    public static class MerchantMatch {
        String canonical;
        double score;
        MatchConfidence confidence;
    }

    /**
     * Confidence levels for fuzzy matches.
     */
    public enum MatchConfidence {
        HIGH,    // score >= 0.90
        MEDIUM,  // score >= 0.80
        LOW,     // score >= 0.70
        NONE     // score < 0.70
    }
}

