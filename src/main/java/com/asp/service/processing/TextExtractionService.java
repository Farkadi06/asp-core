package com.asp.service.processing;

import com.asp.common.Constants;
import com.asp.domain.Ingestion;
import com.asp.parsing.extraction.PdfExtractionException;
import com.asp.parsing.extraction.PdfTextExtractor;
import com.asp.parsing.extraction.TextExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for extracting text from PDF files.
 * Wraps PdfTextExtractor to provide a clean interface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextExtractionService {

    private final PdfTextExtractor pdfTextExtractor;

    /**
     * Extracts text from the PDF associated with the ingestion.
     *
     * @param ingestion the ingestion entity
     * @return the text extraction result
     * @throws PdfExtractionException if extraction fails
     */
    public TextExtractionResult extractText(Ingestion ingestion) throws PdfExtractionException {
        if (ingestion.getStoragePath() == null) {
            throw new IllegalStateException("Ingestion has no storage path");
        }

        log.info("{}[{}] Starting text extraction from S3: {}", 
                Constants.LOG_PREFIX_INGESTION, ingestion.getId(), ingestion.getStoragePath());

        try {
            TextExtractionResult result = pdfTextExtractor.extract(
                    ingestion.getTenant().getId(),
                    ingestion.getId(),
                    ingestion.getStoragePath()
            );

            int textLength = result.getText() != null ? result.getText().length() : 0;
            log.info("{} Extracted text: {} chars, {} pages", 
                    Constants.LOG_PREFIX_PROCESS, textLength, result.getPageCount());

            return result;
        } catch (PdfExtractionException e) {
            log.error("{}[{}] PDF extraction failed: {}", 
                    Constants.LOG_PREFIX_INGESTION, ingestion.getId(), e.getMessage(), e);
            throw e;
        }
    }
}

