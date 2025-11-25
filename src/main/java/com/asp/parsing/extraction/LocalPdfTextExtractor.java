package com.asp.parsing.extraction;

import com.asp.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local implementation of PDF text extractor using PDFBox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalPdfTextExtractor implements PdfTextExtractor {

    private final DocumentStorageService documentStorageService;

    @Value("${asp.storage.local-base-path:./storage}")
    private String localBasePath;

    @Override
    public TextExtractionResult extract(UUID tenantId, UUID ingestionId, String storagePath) throws PdfExtractionException {
        log.debug("[INGESTION:{}] Starting text extraction from path: {}", ingestionId, storagePath);

        try {
            // Resolve full file path
            Path basePath = Paths.get(localBasePath);
            Path fullPath = basePath.resolve(storagePath);

            if (!Files.exists(fullPath)) {
                throw new PdfExtractionException("PDF file not found at path: " + storagePath);
            }

            // Load PDF document
            PDDocument document;
            try (FileInputStream fis = new FileInputStream(fullPath.toFile())) {
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
                metadata.put("fileSize", Files.size(fullPath));
                metadata.put("pageCount", pageCount);
                metadata.put("pagesExtracted", pages.size());
                metadata.put("extractionMethod", "PDFBox");

                // Simple heuristic: if text is very short, might be scanned
                boolean isScanned = text.trim().length() < 100;

                log.info("[INGESTION:{}] Extracted text from PDF: {} pages, {} characters", 
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
            log.error("[INGESTION:{}] Failed to extract text from PDF: {}", ingestionId, e.getMessage(), e);
            throw new PdfExtractionException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }
}

