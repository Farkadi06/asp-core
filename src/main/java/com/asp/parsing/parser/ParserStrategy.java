package com.asp.parsing.parser;

import com.asp.parsing.detection.BankDetectionResult;
import com.asp.parsing.extraction.TextExtractionResult;

import java.util.Set;

/**
 * Interface for bank-specific statement parsers.
 */
public interface ParserStrategy {
    /**
     * Parses extracted text and returns structured data.
     *
     * @param extractionResult the extracted text and per-page breakdown
     * @param detection the bank detection result
     * @param context the ingestion context
     * @return the parsing result
     */
    ParserResult parse(TextExtractionResult extractionResult, BankDetectionResult detection, IngestionContext context);

    /**
     * Checks if this parser can handle the given bank identifier.
     *
     * @param bankIdentifier the bank identifier (slug)
     * @return true if this parser can handle the bank
     */
    boolean canHandle(String bankIdentifier);

    /**
     * Returns the name of this parser.
     *
     * @return parser name
     */
    String getParserName();

    /**
     * Returns the capabilities of this parser.
     *
     * @return set of capabilities
     */
    Set<ParserCapabilities> getCapabilities();
}

