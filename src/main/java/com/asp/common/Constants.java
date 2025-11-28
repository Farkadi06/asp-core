package com.asp.common;

/**
 * Centralized constants for the ASP platform.
 * All hard-coded values should be defined here to improve maintainability.
 */
public final class Constants {

    private Constants() {
        // Utility class - prevent instantiation
    }

    // S3 Storage Paths
    public static final String ORIGINAL_DIR = "original";
    public static final String TENANTS_PREFIX = "tenants";
    public static final String INGESTIONS_DIR = "ingestions";
    public static final String S3_KEY_SEPARATOR = "/";
    public static final String S3_STORAGE_PATH_TEMPLATE = "tenants/%s/ingestions/%s/original/%s";

    // File Extensions
    public static final String PDF_EXTENSION = ".pdf";
    public static final String DEFAULT_FILENAME = "unnamed.pdf";

    // Content Types
    public static final String CONTENT_TYPE_PDF = "application/pdf";

    // HTTP Headers
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    // Temp Directories
    public static final String TEMP_DIR_PREFIX = "/tmp/ingestions";

    // Error Codes
    public static final String ERROR_CODE_STORAGE_ERROR = "STORAGE_ERROR";
    public static final String ERROR_CODE_PDF_ERROR = "PDF_ERROR";
    public static final String ERROR_CODE_DETECTION_ERROR = "DETECTION_ERROR";
    public static final String ERROR_CODE_PARSER_NOT_AVAILABLE = "PARSER_NOT_AVAILABLE";
    public static final String ERROR_CODE_PARSING_ERROR = "PARSING_ERROR";
    public static final String ERROR_CODE_PROCESSING_ERROR = "PROCESSING_ERROR";
    public static final String ERROR_CODE_DETECTION_UNKNOWN_BANK = "DETECTION_UNKNOWN_BANK";
    public static final String ERROR_CODE_DETECTION_MISMATCH = "DETECTION_MISMATCH";
    public static final String ERROR_CODE_PARSER_NOT_FOUND = "PARSER_NOT_FOUND";
    public static final String ERROR_CODE_CONNECTION_BANK_INVALID = "CONNECTION_BANK_INVALID";
    public static final String ERROR_CODE_PDF_WEAK_CONFIDENCE = "PDF_WEAK_CONFIDENCE";
    public static final String ERROR_CODE_PDF_NOT_A_STATEMENT = "PDF_NOT_A_STATEMENT";

    // Logging Prefixes
    public static final String LOG_PREFIX_CORE = "[CORE]";
    public static final String LOG_PREFIX_INGESTION = "[CORE][INGESTION]";
    public static final String LOG_PREFIX_S3 = "[CORE][S3]";
    public static final String LOG_PREFIX_PARSER = "[CORE][PARSER]";
    public static final String LOG_PREFIX_PROCESS = "[PROCESS]";
}

