package com.asp.platform.enrichment.category;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryClassifierService {

    private static final String RULES_PATH = "enrichment/category-rules.json";

    private final ObjectMapper objectMapper;

    private List<CategoryRule> rules = Collections.emptyList();

    @PostConstruct
    public void loadRules() {
        try (InputStream is = new ClassPathResource(RULES_PATH).getInputStream()) {
            List<CategoryRule> parsedRules = objectMapper.readValue(is, new TypeReference<>() {});
            parsedRules.sort(Comparator.comparingInt(CategoryRule::getPriority).reversed());
            this.rules = parsedRules;
            log.info("Loaded {} category rules", rules.size());
        } catch (IOException e) {
            log.error("Failed to load category rules from {}", RULES_PATH, e);
            this.rules = Collections.emptyList();
        }
    }

    public CategoryClassificationResult classify(String description) {
        if (description == null || description.isBlank()) {
            return defaultResult();
        }

        String normalized = normalize(description);

        for (CategoryRule rule : rules) {
            if (matchesRule(normalized, rule)) {
                log.debug("[CATEGORY] matched=\"{}\" category=\"{}\" subcategory=\"{}\"",
                    rule.getPattern(), rule.getCategory(), rule.getSubcategory());
                
                return CategoryClassificationResult.builder()
                    .category(rule.getCategory())
                    .subcategory(rule.getSubcategory())
                    .matchedPattern(rule.getPattern())
                    .confidence(confidenceFor(rule.getPriority()))
                    .build();
            }
        }

        return defaultResult();
    }

    /**
     * Check if normalized description matches the rule pattern.
     * Supports regex matching and word-boundary matching.
     */
    private boolean matchesRule(String normalized, CategoryRule rule) {
        String pattern = rule.getPattern();
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // If regex is enabled, use Pattern matching
        if (Boolean.TRUE.equals(rule.getRegex())) {
            try {
                Pattern regexPattern;
                if (Boolean.TRUE.equals(rule.getWordMatch())) {
                    // Add word boundaries for word-match mode
                    regexPattern = Pattern.compile("\\b" + pattern + "\\b", Pattern.CASE_INSENSITIVE);
                } else {
                    regexPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                }
                return regexPattern.matcher(normalized).find();
            } catch (Exception e) {
                log.warn("Invalid regex pattern: {}, falling back to substring match", pattern);
                // Fallback to substring matching
                return normalized.contains(pattern);
            }
        }

        // If word-match is enabled (without regex), use word-boundary substring matching
        if (Boolean.TRUE.equals(rule.getWordMatch())) {
            String wordBoundaryPattern = "\\b" + Pattern.quote(pattern) + "\\b";
            return Pattern.compile(wordBoundaryPattern, Pattern.CASE_INSENSITIVE)
                .matcher(normalized).find();
        }

        // Default: simple substring matching (case-insensitive after normalization)
        return normalized.contains(pattern);
    }

    private CategoryClassificationResult defaultResult() {
        return CategoryClassificationResult.builder()
            .category("Other")
            .subcategory(null)
            .matchedPattern(null)
            .confidence(CategoryClassificationResult.DetectionConfidence.LOW)
            .build();
    }

    private CategoryClassificationResult.DetectionConfidence confidenceFor(int priority) {
        // HIGH → priority ≥ 95
        if (priority >= 95) {
            return CategoryClassificationResult.DetectionConfidence.HIGH;
        }
        // MEDIUM → priority ≥ 70
        if (priority >= 70) {
            return CategoryClassificationResult.DetectionConfidence.MEDIUM;
        }
        // LOW → below 70
        return CategoryClassificationResult.DetectionConfidence.LOW;
    }

    private String normalize(String input) {
        String upper = input.toUpperCase(Locale.ROOT);
        String normalized = Normalizer.normalize(upper, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }
}

