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
            if (normalized.contains(rule.getPattern())) {
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

    private CategoryClassificationResult defaultResult() {
        return CategoryClassificationResult.builder()
            .category("Other")
            .subcategory(null)
            .matchedPattern(null)
            .confidence(CategoryClassificationResult.DetectionConfidence.LOW)
            .build();
    }

    private CategoryClassificationResult.DetectionConfidence confidenceFor(int priority) {
        if (priority >= 95) {
            return CategoryClassificationResult.DetectionConfidence.HIGH;
        }
        if (priority >= 70) {
            return CategoryClassificationResult.DetectionConfidence.MEDIUM;
        }
        return CategoryClassificationResult.DetectionConfidence.LOW;
    }

    private String normalize(String input) {
        String upper = input.toUpperCase(Locale.ROOT);
        String normalized = Normalizer.normalize(upper, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }
}

