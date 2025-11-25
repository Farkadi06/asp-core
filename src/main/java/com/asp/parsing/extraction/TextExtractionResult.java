package com.asp.parsing.extraction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of PDF text extraction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextExtractionResult {
    private String text;
    private int pageCount;
    private String encoding;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    private boolean isScanned;  // For OCR detection
    @Builder.Default
    private List<String> pages = new ArrayList<>();
}

