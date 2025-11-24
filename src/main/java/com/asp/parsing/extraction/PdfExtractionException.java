package com.asp.parsing.extraction;

/**
 * Exception thrown when PDF text extraction fails.
 */
public class PdfExtractionException extends Exception {
    public PdfExtractionException(String message) {
        super(message);
    }

    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}

