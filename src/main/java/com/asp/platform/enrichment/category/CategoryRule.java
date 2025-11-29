package com.asp.platform.enrichment.category;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategoryRule {

    private String pattern;
    private String category;
    private String subcategory;
    private int priority;
    private Boolean regex;  // Optional: if true, treat pattern as regex
    private Boolean wordMatch;  // Optional: if true, use word boundaries
}

