package com.asp.parsing.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Warning generated during parsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParserWarning {
    private String code;
    private String message;
    private String field;  // Optional: which field had the issue
}

