package com.asp.parsing.detection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of bank detection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankDetectionResult {
    private String bankIdentifier;  // Bank slug
    private DetectionConfidence confidence;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    @Builder.Default
    private List<String> matchedPatterns = new ArrayList<>();

    public enum DetectionConfidence {
        HIGH,    // >90% certainty
        MEDIUM,  // 50-90% certainty
        LOW      // <50% certainty
    }
}

