package com.asp.parsing.processor;

import com.asp.common.Constants;
import com.asp.domain.*;
import com.asp.domain.normalized.NormalizedTransaction;
import com.asp.parsing.detection.BankDetectionResult;
import com.asp.parsing.error.ParserErrorLogger;
import com.asp.parsing.extraction.PdfExtractionException;
import com.asp.parsing.extraction.TextExtractionResult;
import com.asp.parsing.parser.*;
import com.asp.parsing.writer.TransactionWriter;
import com.asp.platform.enrichment.service.EnrichmentProcessor;
import com.asp.platform.enrichment.service.EnrichmentService;
import com.asp.service.processing.BankDetectionService;
import com.asp.service.processing.IngestionStatusManager;
import com.asp.service.processing.StatementParsingService;
import com.asp.service.processing.TextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for DefaultIngestionProcessorService validation logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultIngestionProcessorService Validation Tests")
class DefaultIngestionProcessorServiceTest {

    @Mock
    private IngestionRepository ingestionRepository;

    @Mock
    private TextExtractionService textExtractionService;

    @Mock
    private BankDetectionService bankDetectionService;

    @Mock
    private StatementParsingService statementParsingService;

    @Mock
    private ParserRegistry parserRegistry;

    @Mock
    private TransactionWriter transactionWriter;

    @Mock
    private ParserErrorLogger errorLogger;

    @Mock
    private EnrichmentProcessor enrichmentProcessor;

    @Mock
    private IngestionStatusManager statusManager;

    @InjectMocks
    private DefaultIngestionProcessorService processorService;

    private UUID ingestionId;
    private Ingestion ingestion;
    private Tenant tenant;
    private Bank bank;
    private BankConnection bankConnection;
    private TextExtractionResult extractionResult;
    private BankDetectionResult detectionResult;

    @BeforeEach
    void setUp() {
        ingestionId = UUID.randomUUID();
        
        tenant = Tenant.builder()
            .id(UUID.randomUUID())
            .name("Test Tenant")
            .build();

        bank = Bank.builder()
            .id(UUID.randomUUID())
            .name("CIH Bank")
            .slug("cih")
            .country("MA")
            .build();

        bankConnection = BankConnection.builder()
            .id(UUID.randomUUID())
            .bank(bank)
            .tenant(tenant)
            .build();

        ingestion = Ingestion.builder()
            .id(ingestionId)
            .tenant(tenant)
            .bankConnection(bankConnection)
            .userRef("test-ref")
            .status(IngestionStatus.PENDING)
            .source(IngestionSource.MANUAL_UPLOAD)
            .ingestionType(IngestionType.PDF_STATEMENT)
            .originalFileName("test.pdf")
            .build();

        extractionResult = TextExtractionResult.builder()
            .text("CIH Bank Statement\nAccount: 123456")
            .pageCount(1)
            .build();

        detectionResult = BankDetectionResult.builder()
            .bankIdentifier("cih")
            .confidence(BankDetectionResult.DetectionConfidence.HIGH)
            .metadata(Map.of("source", "pattern_matching", "matchCount", 3))
            .matchedPatterns(List.of("CIH", "Crédit Immobilier et Hôtelier"))
            .build();
    }

    @Test
    @DisplayName("Should fail when bank identifier is null")
    void testNullBankIdentifier_Fails() throws PdfExtractionException {
        // Arrange
        BankDetectionResult nullResult = BankDetectionResult.builder()
            .bankIdentifier(null)
            .confidence(BankDetectionResult.DetectionConfidence.LOW)
            .metadata(Map.of("source", "fallback_not_found"))
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(nullResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert
        verify(statusManager).updateStatus(ingestion, IngestionStatus.PROCESSING, null, null);
        verify(statusManager).markAsFailed(eq(ingestion), 
            eq(Constants.ERROR_CODE_CONNECTION_BANK_INVALID), 
            contains("bank identifier is null"));
        verify(errorLogger).logError(eq(ingestion), any(), 
            eq(Constants.ERROR_CODE_CONNECTION_BANK_INVALID), 
            any(), isNull(), any());
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }

    @Test
    @DisplayName("Should fail when bank identifier is empty")
    void testEmptyBankIdentifier_Fails() throws PdfExtractionException {
        // Arrange
        BankDetectionResult emptyResult = BankDetectionResult.builder()
            .bankIdentifier("")
            .confidence(BankDetectionResult.DetectionConfidence.LOW)
            .metadata(Map.of("source", "fallback_not_found"))
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(emptyResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert
        verify(statusManager).markAsFailed(eq(ingestion), 
            eq(Constants.ERROR_CODE_CONNECTION_BANK_INVALID), 
            contains("bank identifier is null"));
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }

    @Test
    @DisplayName("Should fail on HIGH confidence mismatch")
    void testHighConfidenceMismatch_Fails() throws PdfExtractionException {
        // Arrange: Pattern detects "attijari" but connection is "cih"
        BankDetectionResult mismatchResult = BankDetectionResult.builder()
            .bankIdentifier("attijari")
            .confidence(BankDetectionResult.DetectionConfidence.HIGH)
            .metadata(Map.of(
                "source", "pattern_matching",
                "connection_mismatch", true,
                "pattern_bestBank", "attijari",
                "matchCount", 4
            ))
            .matchedPatterns(List.of("AttijariWafa", "Attijari"))
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(mismatchResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert
        ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);

        verify(statusManager).markAsFailed(eq(ingestion), 
            errorCodeCaptor.capture(), 
            errorMessageCaptor.capture());

        assertEquals(Constants.ERROR_CODE_DETECTION_MISMATCH, errorCodeCaptor.getValue());
        assertTrue(errorMessageCaptor.getValue().contains("Detected bank 'attijari'"));
        assertTrue(errorMessageCaptor.getValue().contains("connection bank 'cih'"));

        verify(errorLogger).logError(eq(ingestion), any(), 
            eq(Constants.ERROR_CODE_DETECTION_MISMATCH), 
            any(), isNull(), any());
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }

    @Test
    @DisplayName("Should fail when parser not found")
    void testParserNotFound_Fails() throws PdfExtractionException {
        // Arrange
        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(detectionResult);
        when(parserRegistry.getParser("cih")).thenReturn(null); // No parser available

        // Act
        processorService.processIngestion(ingestionId);

        // Assert
        ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);

        verify(statusManager).markAsFailed(eq(ingestion), 
            errorCodeCaptor.capture(), 
            errorMessageCaptor.capture());

        assertEquals(Constants.ERROR_CODE_PARSER_NOT_FOUND, errorCodeCaptor.getValue());
        assertTrue(errorMessageCaptor.getValue().contains("No parser registered for bank: cih"));

        verify(errorLogger).logError(eq(ingestion), any(), 
            eq(Constants.ERROR_CODE_PARSER_NOT_FOUND), 
            any(), isNull(), any());
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }

    @Test
    @DisplayName("Should continue processing when validation passes")
    void testValidDetection_ContinuesProcessing() throws PdfExtractionException {
        // Arrange
        ParserStrategy mockParser = mock(ParserStrategy.class);
        ParserResult parserResult = ParserResult.builder()
            .transactions(List.of(
                NormalizedTransaction.builder()
                    .amount(java.math.BigDecimal.ZERO)
                    .build()
            ))
            .build();

        EnrichmentService.EnrichmentResult enrichmentResult = 
            new EnrichmentService.EnrichmentResult(1, 1);

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(detectionResult);
        when(parserRegistry.getParser("cih")).thenReturn(mockParser);
        when(statementParsingService.parseStatement(ingestion, extractionResult, detectionResult))
            .thenReturn(parserResult);
        when(enrichmentProcessor.processIngestion(ingestionId, tenant.getId()))
            .thenReturn(enrichmentResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert
        verify(statusManager).updateStatus(ingestion, IngestionStatus.PROCESSING, null, null);
        verify(parserRegistry).getParser("cih");
        verify(statementParsingService).parseStatement(ingestion, extractionResult, detectionResult);
        verify(statusManager).updateStatus(ingestion, IngestionStatus.SAVING, null, null);
        verify(transactionWriter).saveParserResult(eq(parserResult), any());
        verify(enrichmentProcessor).processIngestion(ingestionId, tenant.getId());
        verify(statusManager).markAsParsed(ingestion);
        verify(statusManager, never()).markAsFailed(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject MEDIUM confidence with mismatch (new strict policy)")
    void testMediumConfidenceMismatch_ContinuesWithFallback() throws PdfExtractionException {
        // Arrange: Pattern detects "attijari" (MEDIUM), connection is "cih"
        // This should NOW fail because MEDIUM confidence with mismatch is rejected
        BankDetectionResult mediumMismatchResult = BankDetectionResult.builder()
            .bankIdentifier("cih") // Fallback uses connection bank
            .confidence(BankDetectionResult.DetectionConfidence.MEDIUM)
            .metadata(Map.of(
                "source", "bank_connection_fallback",
                "connection_mismatch", true,
                "pattern_bestBank", "attijari",
                "pattern_confidence", "MEDIUM",
                "pattern_matchCount", 2
            ))
            .matchedPatterns(List.of("Attijari"))
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(mediumMismatchResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert: Should be rejected with DETECTION_MISMATCH
        verify(statusManager).markAsFailed(eq(ingestion), 
            eq(Constants.ERROR_CODE_DETECTION_MISMATCH), 
            contains("Detected bank 'attijari'"));
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle HIGH confidence without mismatch")
    void testHighConfidenceNoMismatch_ContinuesProcessing() throws PdfExtractionException {
        // Arrange: HIGH confidence, no mismatch
        BankDetectionResult highConfidenceResult = BankDetectionResult.builder()
            .bankIdentifier("cih")
            .confidence(BankDetectionResult.DetectionConfidence.HIGH)
            .metadata(Map.of(
                "source", "pattern_matching",
                "connection_mismatch", false,
                "matchCount", 5
            ))
            .matchedPatterns(List.of("CIH", "CIH Bank"))
            .build();

        ParserStrategy mockParser = mock(ParserStrategy.class);
        ParserResult parserResult = ParserResult.builder()
            .transactions(Collections.emptyList())
            .build();

        EnrichmentService.EnrichmentResult enrichmentResult = 
            new EnrichmentService.EnrichmentResult(0, 0);

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(highConfidenceResult);
        when(parserRegistry.getParser("cih")).thenReturn(mockParser);
        when(statementParsingService.parseStatement(ingestion, extractionResult, highConfidenceResult))
            .thenReturn(parserResult);
        when(enrichmentProcessor.processIngestion(ingestionId, tenant.getId()))
            .thenReturn(enrichmentResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert: Should continue processing
        verify(parserRegistry).getParser("cih");
        verify(statementParsingService).parseStatement(ingestion, extractionResult, highConfidenceResult);
        verify(statusManager).markAsParsed(ingestion);
        verify(statusManager, never()).markAsFailed(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle HIGH confidence with null metadata")
    void testHighConfidenceNullMetadata_ContinuesProcessing() throws PdfExtractionException {
        // Arrange: HIGH confidence but metadata is null (edge case)
        BankDetectionResult highConfidenceResult = BankDetectionResult.builder()
            .bankIdentifier("cih")
            .confidence(BankDetectionResult.DetectionConfidence.HIGH)
            .metadata(null) // Null metadata
            .matchedPatterns(List.of("CIH"))
            .build();

        ParserStrategy mockParser = mock(ParserStrategy.class);
        ParserResult parserResult = ParserResult.builder()
            .transactions(Collections.emptyList())
            .build();

        EnrichmentService.EnrichmentResult enrichmentResult = 
            new EnrichmentService.EnrichmentResult(0, 0);

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(highConfidenceResult);
        when(parserRegistry.getParser("cih")).thenReturn(mockParser);
        when(statementParsingService.parseStatement(ingestion, extractionResult, highConfidenceResult))
            .thenReturn(parserResult);
        when(enrichmentProcessor.processIngestion(ingestionId, tenant.getId()))
            .thenReturn(enrichmentResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert: Should continue processing (null metadata doesn't cause failure)
        verify(parserRegistry).getParser("cih");
        verify(statementParsingService).parseStatement(ingestion, extractionResult, highConfidenceResult);
        verify(statusManager).markAsParsed(ingestion);
        verify(statusManager, never()).markAsFailed(any(), any(), any());
    }

    @Test
    @DisplayName("Should fail when connection bank is null")
    void testConnectionBankNull_Fails() throws PdfExtractionException {
        // Arrange: Connection has no bank
        BankConnection connectionWithoutBank = BankConnection.builder()
            .id(UUID.randomUUID())
            .bank(null) // No bank
            .tenant(tenant)
            .build();

        Ingestion ingestionWithoutBank = Ingestion.builder()
            .id(ingestionId)
            .tenant(tenant)
            .bankConnection(connectionWithoutBank)
            .userRef("test-ref")
            .status(IngestionStatus.PENDING)
            .source(IngestionSource.MANUAL_UPLOAD)
            .ingestionType(IngestionType.PDF_STATEMENT)
            .build();

        BankDetectionResult mismatchResult = BankDetectionResult.builder()
            .bankIdentifier("cih")
            .confidence(BankDetectionResult.DetectionConfidence.HIGH)
            .metadata(Map.of(
                "source", "pattern_matching",
                "connection_mismatch", true,
                "pattern_bestBank", "cih"
            ))
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestionWithoutBank));
        when(statusManager.canProcess(ingestionWithoutBank)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestionWithoutBank);
        when(bankDetectionService.detectBank(ingestionWithoutBank, extractionResult.getText())).thenReturn(mismatchResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert: Should handle null bank gracefully
        verify(statusManager).markAsFailed(eq(ingestionWithoutBank), 
            eq(Constants.ERROR_CODE_DETECTION_MISMATCH), 
            contains("unknown"));
    }

    @Test
    @DisplayName("Should reject LOW confidence detection")
    void testLowConfidence_Rejected() throws PdfExtractionException {
        // Arrange
        BankDetectionResult lowConfidenceResult = BankDetectionResult.builder()
            .bankIdentifier("cih")
            .confidence(BankDetectionResult.DetectionConfidence.LOW)
            .metadata(Map.of("source", "bank_connection_fallback"))
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(lowConfidenceResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert
        verify(statusManager).markAsFailed(eq(ingestion), 
            eq(Constants.ERROR_CODE_PDF_WEAK_CONFIDENCE), 
            eq("Bank detection confidence is too weak to trust this document."));
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject NONE confidence (no patterns detected)")
    void testNoneConfidence_Rejected() throws PdfExtractionException {
        // Arrange: NONE confidence means pattern_confidence == "NONE" in metadata
        // Note: confidence can be LOW but pattern_confidence is "NONE"
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "bank_connection_fallback");
        metadata.put("pattern_confidence", "NONE");
        metadata.put("pattern_bestBank", null);
        
        BankDetectionResult noneConfidenceResult = BankDetectionResult.builder()
            .bankIdentifier("cih") // From fallback
            .confidence(BankDetectionResult.DetectionConfidence.LOW)
            .metadata(metadata)
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(noneConfidenceResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert
        verify(statusManager).markAsFailed(eq(ingestion), 
            eq(Constants.ERROR_CODE_PDF_NOT_A_STATEMENT), 
            eq("The uploaded file does not appear to be a bank statement."));
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject MEDIUM confidence with mismatch")
    void testMediumConfidence_Mismatch_Rejected() throws PdfExtractionException {
        // Arrange: MEDIUM confidence, pattern detects "attijari" but connection is "cih"
        BankDetectionResult mediumMismatchResult = BankDetectionResult.builder()
            .bankIdentifier("cih") // From fallback
            .confidence(BankDetectionResult.DetectionConfidence.MEDIUM)
            .metadata(Map.of(
                "source", "bank_connection_fallback",
                "connection_mismatch", true,
                "pattern_bestBank", "attijari",
                "pattern_confidence", "MEDIUM",
                "pattern_matchCount", 2
            ))
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(mediumMismatchResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert
        ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);

        verify(statusManager).markAsFailed(eq(ingestion), 
            errorCodeCaptor.capture(), 
            errorMessageCaptor.capture());

        assertEquals(Constants.ERROR_CODE_DETECTION_MISMATCH, errorCodeCaptor.getValue());
        assertTrue(errorMessageCaptor.getValue().contains("Detected bank 'attijari'"));
        assertTrue(errorMessageCaptor.getValue().contains("connection bank 'cih'"));
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }

    @Test
    @DisplayName("Should accept MEDIUM confidence when banks match")
    void testMediumConfidence_Match_Allowed() throws PdfExtractionException {
        // Arrange: MEDIUM confidence, pattern detects "cih" and connection is "cih"
        BankDetectionResult mediumMatchResult = BankDetectionResult.builder()
            .bankIdentifier("cih")
            .confidence(BankDetectionResult.DetectionConfidence.MEDIUM)
            .metadata(Map.of(
                "source", "pattern_matching",
                "connection_mismatch", false,
                "pattern_bestBank", "cih",
                "pattern_confidence", "MEDIUM",
                "pattern_matchCount", 2
            ))
            .build();

        ParserStrategy mockParser = mock(ParserStrategy.class);
        ParserResult parserResult = ParserResult.builder()
            .transactions(Collections.emptyList())
            .build();

        EnrichmentService.EnrichmentResult enrichmentResult = 
            new EnrichmentService.EnrichmentResult(0, 0);

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(mediumMatchResult);
        when(parserRegistry.getParser("cih")).thenReturn(mockParser);
        when(statementParsingService.parseStatement(ingestion, extractionResult, mediumMatchResult))
            .thenReturn(parserResult);
        when(enrichmentProcessor.processIngestion(ingestionId, tenant.getId()))
            .thenReturn(enrichmentResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert: Should proceed to parsing
        verify(parserRegistry).getParser("cih");
        verify(statementParsingService).parseStatement(ingestion, extractionResult, mediumMatchResult);
        verify(statusManager).markAsParsed(ingestion);
        verify(statusManager, never()).markAsFailed(any(), any(), any());
    }

    @Test
    @DisplayName("Should accept HIGH confidence (no change from existing behavior)")
    void testHighConfidence_NoChange() throws PdfExtractionException {
        // Arrange: HIGH confidence, no mismatch
        BankDetectionResult highConfidenceResult = BankDetectionResult.builder()
            .bankIdentifier("cih")
            .confidence(BankDetectionResult.DetectionConfidence.HIGH)
            .metadata(Map.of(
                "source", "pattern_matching",
                "connection_mismatch", false,
                "matchCount", 5
            ))
            .matchedPatterns(List.of("CIH", "CIH Bank"))
            .build();

        ParserStrategy mockParser = mock(ParserStrategy.class);
        ParserResult parserResult = ParserResult.builder()
            .transactions(Collections.emptyList())
            .build();

        EnrichmentService.EnrichmentResult enrichmentResult = 
            new EnrichmentService.EnrichmentResult(0, 0);

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(highConfidenceResult);
        when(parserRegistry.getParser("cih")).thenReturn(mockParser);
        when(statementParsingService.parseStatement(ingestion, extractionResult, highConfidenceResult))
            .thenReturn(parserResult);
        when(enrichmentProcessor.processIngestion(ingestionId, tenant.getId()))
            .thenReturn(enrichmentResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert: Should proceed normally
        verify(parserRegistry).getParser("cih");
        verify(statementParsingService).parseStatement(ingestion, extractionResult, highConfidenceResult);
        verify(statusManager).markAsParsed(ingestion);
        verify(statusManager, never()).markAsFailed(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject random PDF with no patterns (fallback_not_found)")
    void testRandomPDF_NoPatterns() throws PdfExtractionException {
        // Arrange: No patterns detected, fallback also fails
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "fallback_not_found");
        metadata.put("pattern_confidence", "NONE");
        metadata.put("pattern_bestBank", null);
        
        BankDetectionResult noPatternsResult = BankDetectionResult.builder()
            .bankIdentifier("cih") // From fallback, but pattern_bestBank is null
            .confidence(BankDetectionResult.DetectionConfidence.LOW)
            .metadata(metadata)
            .build();

        when(ingestionRepository.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(statusManager.canProcess(ingestion)).thenReturn(true);
        doReturn(extractionResult).when(textExtractionService).extractText(ingestion);
        when(bankDetectionService.detectBank(ingestion, extractionResult.getText())).thenReturn(noPatternsResult);

        // Act
        processorService.processIngestion(ingestionId);

        // Assert: Should fail with PDF_NOT_A_STATEMENT (NONE check happens before LOW)
        verify(statusManager).markAsFailed(eq(ingestion), 
            eq(Constants.ERROR_CODE_PDF_NOT_A_STATEMENT), 
            eq("The uploaded file does not appear to be a bank statement."));
        verify(statementParsingService, never()).parseStatement(any(), any(), any());
    }
}

