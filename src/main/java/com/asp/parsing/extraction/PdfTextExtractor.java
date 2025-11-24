package com.asp.parsing.extraction;

import java.util.UUID;

/**
 * Interface for extracting text content from PDF files.
 */
public interface PdfTextExtractor {
    /**
     * Extracts text from a PDF file stored for the given ingestion.
     *
     * @param tenantId the tenant ID
     * @param ingestionId the ingestion ID
     * @param storagePath the storage path to the PDF file
     * @return the text extraction result
     * @throws PdfExtractionException if extraction fails
     */
    TextExtractionResult extract(UUID tenantId, UUID ingestionId, String storagePath) throws PdfExtractionException;
}

