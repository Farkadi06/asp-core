package com.asp.parsing.processor;

import com.asp.common.Constants;
import com.asp.domain.Ingestion;
import com.asp.domain.IngestionRepository;
import com.asp.domain.IngestionStatus;
import com.asp.domain.ParserError;
import com.asp.parsing.detection.BankDetectionResult;
import com.asp.parsing.error.ParserErrorLogger;
import com.asp.parsing.extraction.TextExtractionResult;
import com.asp.parsing.parser.IngestionContext;
import com.asp.parsing.parser.ParserRegistry;
import com.asp.parsing.parser.ParserResult;
import com.asp.parsing.parser.ParserStrategy;
import com.asp.parsing.writer.TransactionWriter;
import com.asp.platform.enrichment.service.EnrichmentProcessor;
import com.asp.platform.enrichment.service.EnrichmentService;
import com.asp.service.processing.BankDetectionService;
import com.asp.service.processing.IngestionStatusManager;
import com.asp.service.processing.StatementParsingService;
import com.asp.service.processing.TextExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TextExtractionService textExtractionService;
    private final BankDetectionService bankDetectionService;
    private final StatementParsingService statementParsingService;
    private final ParserRegistry parserRegistry;
    private final TransactionWriter transactionWriter;
    private final ParserErrorLogger errorLogger;
    private final EnrichmentProcessor enrichmentProcessor;
    private final IngestionStatusManager statusManager;

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
                log.error("{}[{}] Error processing ingestion in batch", Constants.LOG_PREFIX_INGESTION, ingestion.getId(), e);
                // Continue with next ingestion
            }
        }
    }

    @Override
    @Transactional
    public void processIngestion(UUID ingestionId) {
        log.info("{} Starting ingestion processing id={}", Constants.LOG_PREFIX_PROCESS, ingestionId);

        Ingestion ingestion = ingestionRepository.findById(ingestionId)
            .orElseThrow(() -> new IllegalArgumentException("Ingestion not found: " + ingestionId));

        if (!statusManager.canProcess(ingestion)) {
            log.warn("{}[{}] Ingestion is not in PENDING or FAILED state: {}", 
                Constants.LOG_PREFIX_PROCESS, ingestionId, ingestion.getStatus());
            return;
        }

        try {
            // Step 1: Mark as PROCESSING
            statusManager.updateStatus(ingestion, IngestionStatus.PROCESSING, null, null);

            // Step 2: Extract text from PDF
            TextExtractionResult extractionResult = textExtractionService.extractText(ingestion);

            // Step 3: Detect bank
            BankDetectionResult detectionResult = bankDetectionService.detectBank(ingestion, extractionResult.getText());
            String bankIdentifier = detectionResult.getBankIdentifier();
            BankDetectionResult.DetectionConfidence confidence = detectionResult.getConfidence();
            Map<String, Object> metadata = detectionResult.getMetadata();

            // Validation 1: Check if bank identifier is null or invalid
            if (bankIdentifier == null || bankIdentifier.trim().isEmpty()) {
                log.error("{}[{}][DETECTION] Bank detection failed - bank identifier is null or empty", 
                    Constants.LOG_PREFIX_PROCESS, ingestionId);
                markFailed(ingestion, Constants.ERROR_CODE_CONNECTION_BANK_INVALID, 
                    "Could not detect bank from statement - bank identifier is null", 
                    null, Map.of("detection", detectionResult));
                return;
            }

            // Validation 2: NONE confidence (zero matches) → ALWAYS reject
            // Check metadata for pattern_confidence == "NONE" or pattern_bestBank == null
            String patternConfidence = metadata != null ? (String) metadata.get("pattern_confidence") : null;
            if ("NONE".equals(patternConfidence) || 
                (metadata != null && metadata.get("pattern_bestBank") == null && 
                 "fallback_not_found".equals(metadata.get("source")))) {
                log.warn("{}[{}][DETECTION] Rejecting PDF: NO bank patterns detected in the document", 
                    Constants.LOG_PREFIX_PROCESS, ingestionId);
                markFailed(ingestion, Constants.ERROR_CODE_PDF_NOT_A_STATEMENT, 
                    "The uploaded file does not appear to be a bank statement.", 
                    null, Map.of("detection", detectionResult));
                return;
            }

            // Validation 3: LOW confidence → ALWAYS reject (but not NONE)
            if (confidence == BankDetectionResult.DetectionConfidence.LOW) {
                log.warn("{}[{}][DETECTION] Rejecting PDF: LOW confidence for bank detection", 
                    Constants.LOG_PREFIX_PROCESS, ingestionId);
                markFailed(ingestion, Constants.ERROR_CODE_PDF_WEAK_CONFIDENCE, 
                    "Bank detection confidence is too weak to trust this document.", 
                    null, Map.of("detection", detectionResult));
                return;
            }

            // Validation 4: MEDIUM confidence → accept ONLY if banks match
            if (confidence == BankDetectionResult.DetectionConfidence.MEDIUM) {
                boolean mismatch = metadata != null && Boolean.TRUE.equals(metadata.get("connection_mismatch"));
                if (mismatch) {
                    String patternBank = metadata != null ? (String) metadata.get("pattern_bestBank") : null;
                    String connectionBank = ingestion.getBankConnection().getBank() != null 
                        ? ingestion.getBankConnection().getBank().getSlug() 
                        : "unknown";
                    
                    log.warn("{}[{}][DETECTION] Rejecting PDF: MEDIUM confidence mismatch between pattern '{}' and connection '{}'", 
                        Constants.LOG_PREFIX_PROCESS, ingestionId, patternBank, connectionBank);
                    
                    String errorMessage = String.format(
                        "Detected bank '%s' does not match connection bank '%s'", 
                        patternBank, connectionBank);
                    
                    markFailed(ingestion, Constants.ERROR_CODE_DETECTION_MISMATCH, 
                        errorMessage, null, Map.of(
                            "detection", detectionResult,
                            "patternBank", patternBank,
                            "connectionBank", connectionBank
                        ));
                    return;
                }
            }

            // Validation 5: HIGH confidence → check for mismatch (existing logic)
            if (confidence == BankDetectionResult.DetectionConfidence.HIGH) {
                if (metadata != null && Boolean.TRUE.equals(metadata.get("connection_mismatch"))) {
                    String patternBank = (String) metadata.get("pattern_bestBank");
                    String connectionBank = ingestion.getBankConnection().getBank() != null 
                        ? ingestion.getBankConnection().getBank().getSlug() 
                        : "unknown";
                    
                    String errorMessage = String.format(
                        "Detected bank '%s' does not match connection bank '%s'", 
                        patternBank, connectionBank);
                    
                    log.error("{}[{}][DETECTION] Bank mismatch: pattern='{}', connection='{}'", 
                        Constants.LOG_PREFIX_PROCESS, ingestionId, patternBank, connectionBank);
                    
                    markFailed(ingestion, Constants.ERROR_CODE_DETECTION_MISMATCH, 
                        errorMessage, null, Map.of(
                            "detection", detectionResult,
                            "patternBank", patternBank,
                            "connectionBank", connectionBank
                        ));
                    return;
                }
            }

            // Validation 3: Check if parser exists for detected bank
            ParserStrategy parser = parserRegistry.getParser(bankIdentifier);
            if (parser == null) {
                log.error("{}[{}][PARSER] No parser found for detected bank: {}", 
                    Constants.LOG_PREFIX_PROCESS, ingestionId, bankIdentifier);
                markFailed(ingestion, Constants.ERROR_CODE_PARSER_NOT_FOUND, 
                    "No parser registered for bank: " + bankIdentifier, 
                    null, Map.of(
                        "bankIdentifier", bankIdentifier,
                        "detection", detectionResult
                    ));
                return;
            }
            
            // Step 4: Parse statement
            ParserResult parserResult = statementParsingService.parseStatement(ingestion, extractionResult, detectionResult);
            int snapshotsCount = parserResult.getSnapshots() != null ? parserResult.getSnapshots().size() : 0;
            log.info("{} Snapshot parsed = {}", Constants.LOG_PREFIX_PROCESS, snapshotsCount > 0 ? "yes" : "no");

            // Step 5: Save to database
            statusManager.updateStatus(ingestion, IngestionStatus.SAVING, null, null);
            log.info("{} Saving transactions... (count: {})", Constants.LOG_PREFIX_PROCESS,
                parserResult.getTransactions() != null ? parserResult.getTransactions().size() : 0);

            saveData(ingestion, parserResult, detectionResult);
            
            // Step 6: Enrich transactions
            EnrichmentService.EnrichmentResult enrichmentResult =
                enrichmentProcessor.processIngestion(ingestion.getId(), ingestion.getTenant().getId());
            log.info("{} Enriched transactions: {} enriched (from {} normalized)",
                Constants.LOG_PREFIX_PROCESS, enrichmentResult.enrichedCount(), enrichmentResult.normalizedCount());

            // Step 7: Mark as PARSED and set processed_at
            statusManager.markAsParsed(ingestion);

        } catch (Exception e) {
            log.error("{}[{}] Error processing ingestion", Constants.LOG_PREFIX_INGESTION, ingestionId, e);
            markFailed(ingestion, Constants.ERROR_CODE_PROCESSING_ERROR, 
                "Error during processing: " + e.getMessage(), e, Map.of());
        }
    }

    @Override
    @Transactional
    public void retryFailedIngestion(UUID ingestionId) {
        log.info("{}[{}] Retrying failed ingestion", Constants.LOG_PREFIX_INGESTION, ingestionId);

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
            log.error("{}[{}] Storage error", Constants.LOG_PREFIX_INGESTION, ingestion.getId(), e);
            markFailed(ingestion, Constants.ERROR_CODE_STORAGE_ERROR, "Failed to save data: " + e.getMessage(), 
                e, Map.of());
            throw e;
        }
    }


    private void markFailed(Ingestion ingestion, String errorCode, String errorMessage, 
                           Throwable throwable, Map<String, Object> context) {
        statusManager.markAsFailed(ingestion, errorCode, errorMessage);
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
            // PDF_WEAK_CONFIDENCE and PDF_NOT_A_STATEMENT are validation errors
            if (errorCode.equals(Constants.ERROR_CODE_PDF_WEAK_CONFIDENCE) || 
                errorCode.equals(Constants.ERROR_CODE_PDF_NOT_A_STATEMENT)) {
                return ParserError.ErrorCategory.VALIDATION_ERROR;
            }
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

