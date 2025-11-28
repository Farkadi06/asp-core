package com.asp.service.processing;

import com.asp.common.Constants;
import com.asp.domain.Ingestion;
import com.asp.parsing.detection.BankDetectionResult;
import com.asp.parsing.extraction.TextExtractionResult;
import com.asp.parsing.parser.IngestionContext;
import com.asp.parsing.parser.ParserRegistry;
import com.asp.parsing.parser.ParserResult;
import com.asp.parsing.parser.ParserStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for parsing bank statements.
 * Wraps ParserRegistry and parser execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatementParsingService {

    private final ParserRegistry parserRegistry;

    /**
     * Parses the statement using the appropriate parser for the detected bank.
     *
     * @param ingestion the ingestion entity
     * @param extractionResult the text extraction result
     * @param detectionResult the bank detection result
     * @return the parsing result
     */
    public ParserResult parseStatement(Ingestion ingestion, 
                                      TextExtractionResult extractionResult,
                                      BankDetectionResult detectionResult) {
        String bankIdentifier = detectionResult.getBankIdentifier();
        
        if (bankIdentifier == null) {
            throw new IllegalStateException("Bank identifier is null - cannot parse statement");
        }

        ParserStrategy parser = parserRegistry.getParser(bankIdentifier);
        log.info("{} Using parser: {} for bank: {}", 
                Constants.LOG_PREFIX_PROCESS, 
                parser != null ? parser.getParserName() : "NULL", 
                bankIdentifier);

        if (parser == null) {
            throw new IllegalStateException("No parser available for bank: " + bankIdentifier);
        }

        try {
            IngestionContext context = IngestionContext.builder()
                    .ingestionId(ingestion.getId())
                    .tenantId(ingestion.getTenant().getId())
                    .bankConnectionId(ingestion.getBankConnection().getId())
                    .userRef(ingestion.getUserRef())
                    .bankIdentifier(bankIdentifier)
                    .build();

            ParserResult result = parser.parse(extractionResult, detectionResult, context);
            
            int transactionsCount = result.getTransactions() != null ? result.getTransactions().size() : 0;
            log.info("{}[{}] Parsed {} transactions", 
                    Constants.LOG_PREFIX_PARSER, bankIdentifier, transactionsCount);

            if (transactionsCount == 0) {
                log.warn("{} WARNING: ParserResult.transactions is EMPTY! Check parser logs for details.", 
                        Constants.LOG_PREFIX_PROCESS);
            }

            return result;
        } catch (Exception e) {
            log.error("{}[{}] Parsing error: {}", 
                    Constants.LOG_PREFIX_INGESTION, ingestion.getId(), e.getMessage(), e);
            throw new RuntimeException("Parsing failed: " + e.getMessage(), e);
        }
    }
}

