package com.asp.platform.enrichment.category;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CategoryClassificationResult {

    String category;
    String subcategory;
    String matchedPattern;
    DetectionConfidence confidence;

    public enum DetectionConfidence {
        HIGH,
        MEDIUM,
        LOW
    }
}

