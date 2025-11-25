package com.asp.parsing.processor;

import com.asp.domain.Ingestion;
import com.asp.domain.IngestionRepository;
import com.asp.domain.IngestionStatus;
import com.asp.parsing.detection.BankDetector;
import com.asp.parsing.detection.BankDetectionResult;
import com.asp.domain.ParserError;
import com.asp.parsing.error.ParserErrorLogger;
import com.asp.parsing.extraction.PdfExtractionException;
import com.asp.parsing.extraction.PdfTextExtractor;
import com.asp.parsing.extraction.TextExtractionResult;
import com.asp.parsing.parser.IngestionContext;
import com.asp.parsing.parser.ParserRegistry;
import com.asp.parsing.parser.ParserResult;
import com.asp.parsing.parser.ParserStrategy;
import com.asp.parsing.writer.TransactionWriter;
import com.asp.platform.enrichment.service.EnrichmentProcessor;
import com.asp.platform.enrichment.service.EnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of IngestionProcessorService.
 * Orchestrates the entire parsing pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultIngestionProcessorService implements IngestionProcessorService {

    private final IngestionRepository ingestionRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final BankDetector bankDetector;
    private final ParserRegistry parserRegistry;
    private final TransactionWriter transactionWriter;
    private final ParserErrorLogger errorLogger;
    private final EnrichmentProcessor enrichmentProcessor;

    @Value("${asp.ingestion.processor.batch-size:5}")
    private int batchSize;

    @Override
    @Transactional
    public void processPendingIngestions() {
        log.debug("Processing pending ingestions (batch size: {})", batchSize);

        List<Ingestion> pendingIngestions = ingestionRepository.findAllByStatus(IngestionStatus.PENDING)
            .stream()
            .limit(batchSize)
            .toList();

        if (pendingIngestions.isEmpty()) {
            log.debug("No pending ingestions to process");
            return;
        }

        log.info("Found {} pending ingestions to process", pendingIngestions.size());

        for (Ingestion ingestion : pendingIngestions) {
            try {
                processIngestion(ingestion.getId());
            } catch (Exception e) {
                log.error("[INGESTION:{}] Error processing ingestion in batch", ingestion.getId(), e);
                // Continue with next ingestion
            }
        }
    }

    @Override
    @Transactional
    public void processIngestion(UUID ingestionId) {
        log.info("[INGESTION:{}] Starting ingestion processing", ingestionId);

        Ingestion ingestion = ingestionRepository.findById(ingestionId)
            .orElseThrow(() -> new IllegalArgumentException("Ingestion not found: " + ingestionId));

        if (ingestion.getStatus() != IngestionStatus.PENDING && ingestion.getStatus() != IngestionStatus.FAILED) {
            log.warn("[INGESTION:{}] Ingestion is not in PENDING or FAILED state: {}", 
                ingestionId, ingestion.getStatus());
            return;
        }

        try {
            // Step 1: Mark as PROCESSING
            updateStatus(ingestion, IngestionStatus.PROCESSING, null, null);
            log.info("[INGESTION:{}] Status updated to PROCESSING", ingestionId);

            // Step 2: Extract text from PDF
            TextExtractionResult extractionResult = extractText(ingestion);
            int textLength = extractionResult.getText() != null ? extractionResult.getText().length() : 0;
            log.info("[INGESTION:{}] Extracted text length = {} characters, pages = {}",
                ingestionId, textLength, extractionResult.getPageCount());

            // Step 3: Detect bank
            BankDetectionResult detectionResult = detectBank(ingestion, extractionResult.getText());
            String bankIdentifier = detectionResult.getBankIdentifier();
            log.info("[INGESTION:{}] Bank detected = {} (confidence: {})",
                ingestionId, bankIdentifier, detectionResult.getConfidence());

            if (bankIdentifier == null) {
                log.error("[INGESTION:{}] Bank detection failed - bank identifier is null", ingestionId);
                markFailed(ingestion, "DETECTION_UNKNOWN_BANK", 
                    "Could not detect bank from statement", 
                    null, Map.of("detection", detectionResult));
                return;
            }

            // Step 4: Get parser and parse
            ParserStrategy parser = parserRegistry.getParser(bankIdentifier);
            log.info("[INGESTION:{}] Parser = {} for bank {}", 
                ingestionId, parser != null ? parser.getParserName() : "NULL", bankIdentifier);
            
            if (parser == null) {
                log.error("[INGESTION:{}] No parser found for bank: {}", ingestionId, bankIdentifier);
                markFailed(ingestion, "PARSER_NOT_AVAILABLE", 
                    "No parser available for bank: " + bankIdentifier,
                    null, Map.of("bankIdentifier", bankIdentifier));
                return;
            }
            
            ParserResult parserResult = parseStatement(ingestion, extractionResult, detectionResult);
            int accountsCount = parserResult.getAccounts() != null ? parserResult.getAccounts().size() : 0;
            int transactionsCount = parserResult.getTransactions() != null ? parserResult.getTransactions().size() : 0;
            int snapshotsCount = parserResult.getSnapshots() != null ? parserResult.getSnapshots().size() : 0;
            
            log.info("[INGESTION:{}] Parsing completed: {} accounts, {} transactions, {} snapshots",
                ingestionId, accountsCount, transactionsCount, snapshotsCount);
            
            if (transactionsCount == 0) {
                log.warn("[INGESTION:{}] WARNING: ParserResult.transactions is EMPTY! Check parser logs for details.", 
                    ingestionId);
            }
            
            log.info("[INGESTION:{}] Snapshot parsed = {}", 
                ingestionId, snapshotsCount > 0 ? "yes" : "no");

            // Step 5: Mark as PARSED
            updateStatus(ingestion, IngestionStatus.PARSED, null, null);
            log.info("[INGESTION:{}] Status updated to PARSED", ingestionId);

            // Step 6: Save to database
            updateStatus(ingestion, IngestionStatus.SAVING, null, null);
            log.info("[INGESTION:{}] Status updated to SAVING", ingestionId);
            log.info("[INGESTION:{}] Saving transactions... (count: {})", 
                ingestionId, parserResult.getTransactions() != null ? parserResult.getTransactions().size() : 0);

            saveData(ingestion, parserResult, detectionResult);
            EnrichmentService.EnrichmentResult enrichmentResult =
                enrichmentProcessor.processIngestion(ingestion.getId(), ingestion.getTenant().getId());
            log.info("[INGESTION:{}] Enrichment persisted {} rows (from {} normalized)",
                ingestionId, enrichmentResult.enrichedCount(), enrichmentResult.normalizedCount());

            // Step 7: Mark as PROCESSED
            ingestion.setStatus(IngestionStatus.PROCESSED);
            ingestion.setProcessedAt(Instant.now());
            ingestionRepository.save(ingestion);
            log.info("[INGESTION:{}] Status updated to PROCESSED", ingestionId);

        } catch (Exception e) {
            log.error("[INGESTION:{}] Error processing ingestion", ingestionId, e);
            markFailed(ingestion, "PROCESSING_ERROR", 
                "Error during processing: " + e.getMessage(), e, Map.of());
        }
    }

    @Override
    @Transactional
    public void retryFailedIngestion(UUID ingestionId) {
        log.info("[INGESTION:{}] Retrying failed ingestion", ingestionId);

        Ingestion ingestion = ingestionRepository.findById(ingestionId)
            .orElseThrow(() -> new IllegalArgumentException("Ingestion not found: " + ingestionId));

        if (ingestion.getStatus() != IngestionStatus.FAILED) {
            throw new IllegalStateException("Ingestion is not in FAILED state: " + ingestion.getStatus());
        }

        // Reset error fields
        ingestion.setErrorCode(null);
        ingestion.setErrorMessage(null);

        // Process again
        processIngestion(ingestionId);
    }

    private TextExtractionResult extractText(Ingestion ingestion) throws Exception {
        if (ingestion.getStoragePath() == null) {
            throw new IllegalStateException("Ingestion has no storage path");
        }

        try {
            return pdfTextExtractor.extract(
                ingestion.getTenant().getId(),
                ingestion.getId(),
                ingestion.getStoragePath()
            );
        } catch (PdfExtractionException e) {
            markFailed(ingestion, "PDF_ERROR", "PDF extraction failed: " + e.getMessage(), 
                e, Map.of("storagePath", ingestion.getStoragePath()));
            throw e;
        }
    }

    private BankDetectionResult detectBank(Ingestion ingestion, String extractedText) {
        try {
            return bankDetector.detect(extractedText, ingestion.getBankConnection().getId());
        } catch (Exception e) {
            log.error("[INGESTION:{}] Bank detection error", ingestion.getId(), e);
            markFailed(ingestion, "DETECTION_ERROR", "Bank detection failed: " + e.getMessage(), 
                e, Map.of());
            throw e;
        }
    }

    private ParserResult parseStatement(Ingestion ingestion, TextExtractionResult extractionResult,
                                       BankDetectionResult detection) {
        ParserStrategy parser = parserRegistry.getParser(detection.getBankIdentifier());
        if (parser == null) {
            markFailed(ingestion, "PARSER_NOT_AVAILABLE", 
                "No parser available for bank: " + detection.getBankIdentifier(),
                null, Map.of("bankIdentifier", detection.getBankIdentifier()));
            throw new IllegalStateException("Parser not found for bank: " + detection.getBankIdentifier());
        }

        try {
            IngestionContext context = IngestionContext.builder()
                .ingestionId(ingestion.getId())
                .tenantId(ingestion.getTenant().getId())
                .bankConnectionId(ingestion.getBankConnection().getId())
                .userRef(ingestion.getUserRef())
                .bankIdentifier(detection.getBankIdentifier())
                .build();

            return parser.parse(extractionResult, detection, context);
        } catch (Exception e) {
            log.error("[INGESTION:{}] Parsing error", ingestion.getId(), e);
            markFailed(ingestion, "PARSING_ERROR", "Parsing failed: " + e.getMessage(), 
                e, Map.of("parser", parser.getParserName()));
            throw e;
        }
    }

    private void saveData(Ingestion ingestion, ParserResult parserResult, 
                         BankDetectionResult detection) {
        try {
            IngestionContext context = IngestionContext.builder()
                .ingestionId(ingestion.getId())
                .tenantId(ingestion.getTenant().getId())
                .bankConnectionId(ingestion.getBankConnection().getId())
                .userRef(ingestion.getUserRef())
                .bankIdentifier(detection.getBankIdentifier())
                .build();

            transactionWriter.saveParserResult(parserResult, context);
        } catch (Exception e) {
            log.error("[INGESTION:{}] Storage error", ingestion.getId(), e);
            markFailed(ingestion, "STORAGE_ERROR", "Failed to save data: " + e.getMessage(), 
                e, Map.of());
            throw e;
        }
    }

    private void updateStatus(Ingestion ingestion, IngestionStatus status, 
                            String errorCode, String errorMessage) {
        ingestion.setStatus(status);
        if (errorCode != null) {
            ingestion.setErrorCode(errorCode);
        }
        if (errorMessage != null) {
            ingestion.setErrorMessage(errorMessage);
        }
        ingestionRepository.save(ingestion);
    }

    private void markFailed(Ingestion ingestion, String errorCode, String errorMessage, 
                           Throwable throwable, Map<String, Object> context) {
        updateStatus(ingestion, IngestionStatus.FAILED, errorCode, errorMessage);
        errorLogger.logError(ingestion, 
            determineErrorCategory(errorCode), 
            errorCode, 
            errorMessage, 
            throwable, 
            context);
    }

    private ParserError.ErrorCategory determineErrorCategory(String errorCode) {
        if (errorCode == null) {
            return ParserError.ErrorCategory.VALIDATION_ERROR;
        }
        if (errorCode.startsWith("PDF_")) {
            return ParserError.ErrorCategory.PDF_ERROR;
        } else if (errorCode.startsWith("DETECTION_")) {
            return ParserError.ErrorCategory.DETECTION_ERROR;
        } else if (errorCode.startsWith("PARSER_") || errorCode.startsWith("PARSING_")) {
            return ParserError.ErrorCategory.PARSING_ERROR;
        } else if (errorCode.startsWith("STORAGE_")) {
            return ParserError.ErrorCategory.STORAGE_ERROR;
        } else {
            return ParserError.ErrorCategory.VALIDATION_ERROR;
        }
    }
}

