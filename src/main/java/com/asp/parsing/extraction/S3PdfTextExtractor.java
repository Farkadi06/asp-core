package com.asp.parsing.extraction;

import com.asp.service.storage.S3DownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S3-based PDF text extractor.
 * Downloads PDF from S3 to temp, extracts text, then cleans up.
 * This is the primary extractor for S3-based storage.
 */
@Component
@org.springframework.context.annotation.Primary
@Slf4j
@RequiredArgsConstructor
public class S3PdfTextExtractor implements PdfTextExtractor {

    private final S3DownloadService s3DownloadService;

    @Override
    public TextExtractionResult extract(UUID tenantId, UUID ingestionId, String storagePath) throws PdfExtractionException {
        log.debug("[PROCESS][INGESTION:{}] Starting text extraction from S3: {}", ingestionId, storagePath);

        Path tempFilePath = null;
        try {
            // Download from S3 to temp
            tempFilePath = s3DownloadService.downloadToTemp(storagePath, ingestionId);
            if (tempFilePath == null || !Files.exists(tempFilePath)) {
                throw new PdfExtractionException("Failed to download PDF from S3: " + storagePath);
            }

            log.info("[PROCESS][INGESTION:{}] Downloaded PDF from S3: size={} KB, path={}",
                    ingestionId, Files.size(tempFilePath) / 1024, tempFilePath);

            // Load PDF document
            PDDocument document;
            try (FileInputStream fis = new FileInputStream(tempFilePath.toFile())) {
                document = Loader.loadPDF(fis.readAllBytes());
            }

            try {
                int pageCount = document.getNumberOfPages();

                // Extract full text
                PDFTextStripper fullStripper = new PDFTextStripper();
                String text = fullStripper.getText(document);

                // Extract per-page text content
                List<String> pages = new ArrayList<>(pageCount);
                for (int page = 1; page <= pageCount; page++) {
                    PDFTextStripper pageStripper = new PDFTextStripper();
                    pageStripper.setStartPage(page);
                    pageStripper.setEndPage(page);
                    String pageText = pageStripper.getText(document);
                    pages.add(pageText != null ? pageText : "");
                }

                // Build metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("fileSize", Files.size(tempFilePath));
                metadata.put("pageCount", pageCount);
                metadata.put("pagesExtracted", pages.size());
                metadata.put("extractionMethod", "PDFBox-S3");

                // Simple heuristic: if text is very short, might be scanned
                boolean isScanned = text.trim().length() < 100;

                log.info("[PROCESS][INGESTION:{}] Extracted text from PDF: {} pages, {} characters",
                        ingestionId, pageCount, text.length());

                return TextExtractionResult.builder()
                        .text(text)
                        .pageCount(pageCount)
                        .encoding("UTF-8")
                        .metadata(metadata)
                        .pages(pages)
                        .isScanned(isScanned)
                        .build();

            } finally {
                document.close();
            }

        } catch (IOException e) {
            log.error("[PROCESS][INGESTION:{}] Failed to extract text from PDF: {}", ingestionId, e.getMessage(), e);
            throw new PdfExtractionException("Failed to extract text from PDF: " + e.getMessage(), e);
        } finally {
            // Clean up temp file
            if (tempFilePath != null) {
                try {
                    Files.deleteIfExists(tempFilePath);
                    log.debug("[PROCESS][INGESTION:{}] Cleaned up temp file: {}", ingestionId, tempFilePath);
                } catch (IOException e) {
                    log.warn("[PROCESS][INGESTION:{}] Failed to delete temp file: {}", ingestionId, tempFilePath, e);
                }
            }
        }
    }
}

