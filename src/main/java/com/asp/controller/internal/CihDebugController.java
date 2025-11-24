package com.asp.controller.internal;

import com.asp.domain.Ingestion;
import com.asp.domain.IngestionRepository;
import com.asp.domain.normalized.NormalizedTransaction;
import com.asp.parsing.extraction.PdfExtractionException;
import com.asp.parsing.extraction.PdfTextExtractor;
import com.asp.parsing.extraction.TextExtractionResult;
import com.asp.parsing.parser.IngestionContext;
import com.asp.parsing.parser.ParserWarning;
import com.asp.parsing.parser.impl.CihPdfParser;
import com.asp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Debug controller for CIH parser testing without DB writes.
 */
@RestController
@RequestMapping("/internal/debug")
@RequiredArgsConstructor
@Slf4j
public class CihDebugController {

    private final IngestionRepository ingestionRepository;
    private final PdfTextExtractor pdfTextExtractor;

    @GetMapping("/cih-detect")
    public ResponseEntity<Map<String, Object>> debugCihParsing(@RequestParam UUID ingestionId) {
        log.info("[DEBUG][CIH] Debug parsing for ingestion: {}", ingestionId);

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.warn("[DEBUG][CIH] Tenant context not set");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context not available");
        }

        // Load ingestion
        Ingestion ingestion = ingestionRepository.findByIdAndTenantId(ingestionId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Ingestion not found: " + ingestionId));

        if (ingestion.getStoragePath() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Ingestion has no storage path");
        }

        Map<String, Object> response = new HashMap<>();

        try {
            // Extract text from PDF
            log.info("[DEBUG][CIH] Extracting text from PDF...");
            TextExtractionResult extractionResult = pdfTextExtractor.extract(
                tenantId,
                ingestionId,
                ingestion.getStoragePath()
            );

            String extractedText = extractionResult.getText();
            response.put("extractedTextLength", extractedText != null ? extractedText.length() : 0);
            response.put("pageCount", extractionResult.getPageCount());

            if (extractedText == null || extractedText.trim().isEmpty()) {
                response.put("error", "Extracted text is empty");
                return ResponseEntity.ok(response);
            }

            // Split into lines for analysis
            String[] lines = extractedText.split("\\r?\\n");
            List<Map<String, Object>> rawLines = new ArrayList<>();
            for (int i = 0; i < lines.length; i++) {
                Map<String, Object> lineInfo = new HashMap<>();
                lineInfo.put("lineNumber", i + 1);
                lineInfo.put("content", lines[i]);
                lineInfo.put("trimmed", lines[i].trim());
                lineInfo.put("isEmpty", lines[i].trim().isEmpty());
                rawLines.add(lineInfo);
            }
            response.put("rawLines", rawLines);
            response.put("totalLines", lines.length);

            // Create a minimal IngestionContext for parsing
            IngestionContext context = IngestionContext.builder()
                .ingestionId(ingestionId)
                .tenantId(tenantId)
                .bankConnectionId(ingestion.getBankConnection().getId())
                .userRef(ingestion.getUserRef())
                .bankIdentifier("cih")
                .build();

            // Run transaction parsing in isolation
            log.info("[DEBUG][CIH] Running transaction parsing in isolation...");
            
            // We need to create a helper method that can be called here
            // For now, let's parse account number and opening balance first
            List<ParserWarning> warnings = new ArrayList<>();
            
            // Call a helper method that extracts just the transaction parsing logic
            List<NormalizedTransaction> transactions = parseTransactionsForDebug(
                extractedText,
                "UNKNOWN", // Will be determined from parsing
                "MAD",
                LocalDate.now(),
                warnings,
                context
            );

            // Build matched/skipped lines info
            List<Map<String, Object>> matchedLines = new ArrayList<>();
            List<Map<String, Object>> skippedLines = new ArrayList<>();

            // Re-analyze lines to determine which matched
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                Map<String, Object> lineInfo = new HashMap<>();
                lineInfo.put("lineNumber", i + 1);
                lineInfo.put("content", line);
                
                // Check if this line matches transaction pattern
                java.util.regex.Pattern TRANSACTION_LINE_PATTERN = 
                    java.util.regex.Pattern.compile("^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})\\s+(.*)$");
                java.util.regex.Matcher matcher = TRANSACTION_LINE_PATTERN.matcher(line);
                
                if (matcher.matches()) {
                    lineInfo.put("matched", true);
                    lineInfo.put("operDate", matcher.group(1));
                    lineInfo.put("valDate", matcher.group(2));
                    lineInfo.put("rest", matcher.group(3));
                    matchedLines.add(lineInfo);
                } else {
                    lineInfo.put("matched", false);
                    skippedLines.add(lineInfo);
                }
            }

            response.put("matchedLines", matchedLines);
            response.put("skippedLines", skippedLines);

            // Convert transactions to maps for JSON response
            List<Map<String, Object>> parsedTransactions = transactions.stream()
                .map(tx -> {
                    Map<String, Object> txMap = new HashMap<>();
                    txMap.put("date", tx.getTransactionDate());
                    txMap.put("valueDate", tx.getValueDate());
                    txMap.put("amount", tx.getAmount());
                    txMap.put("type", tx.getType());
                    txMap.put("description", tx.getDescription());
                    return txMap;
                })
                .collect(Collectors.toList());

            response.put("parsedTransactions", parsedTransactions);
            response.put("transactionCount", transactions.size());

            // Warnings
            List<Map<String, Object>> warningMaps = warnings.stream()
                .map(w -> {
                    Map<String, Object> wMap = new HashMap<>();
                    wMap.put("code", w.getCode());
                    wMap.put("message", w.getMessage());
                    wMap.put("field", w.getField());
                    return wMap;
                })
                .collect(Collectors.toList());
            response.put("warnings", warningMaps);
            response.put("warningCount", warnings.size());

            response.put("status", "success");

        } catch (PdfExtractionException e) {
            log.error("[DEBUG][CIH] PDF extraction error", e);
            response.put("status", "error");
            response.put("error", "PDF extraction failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            log.error("[DEBUG][CIH] Unexpected error", e);
            response.put("status", "error");
            response.put("error", "Unexpected error: " + e.getMessage());
            response.put("errorType", e.getClass().getName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to parse transactions for debugging (isolated from full parsing).
     * This mimics the parseTransactions logic from CihPdfParser.
     */
    private List<NormalizedTransaction> parseTransactionsForDebug(
            String text,
            String accountNumber,
            String currency,
            LocalDate openingDate,
            List<ParserWarning> warnings,
            IngestionContext context) {
        
        List<NormalizedTransaction> transactions = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        
        int year = openingDate != null ? openingDate.getYear() : LocalDate.now().getYear();
        java.util.regex.Pattern TRANSACTION_LINE_PATTERN = 
            java.util.regex.Pattern.compile("^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})\\s+(.*)$");
        java.util.regex.Pattern DESCRIPTION_AMOUNT_PATTERN = 
            java.util.regex.Pattern.compile("(.+?)\\s+([0-9][0-9 \\.,]+)$");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            java.util.regex.Matcher lineMatcher = TRANSACTION_LINE_PATTERN.matcher(line);
            if (!lineMatcher.matches()) {
                continue;
            }

            try {
                String operDateStr = lineMatcher.group(1);
                String valeurDateStr = lineMatcher.group(2);
                String restOfLine = lineMatcher.group(3);

                // Parse dates
                LocalDate operDate = parseDate(operDateStr, year);
                LocalDate valeurDate = parseDate(valeurDateStr, year);
                
                if (operDate == null) {
                    continue;
                }

                // Extract description and amount
                java.util.regex.Matcher descAmountMatcher = DESCRIPTION_AMOUNT_PATTERN.matcher(restOfLine);
                if (!descAmountMatcher.matches()) {
                    continue;
                }

                String description = descAmountMatcher.group(1).trim();
                String amountStr = descAmountMatcher.group(2).trim();

                // Parse amount
                java.math.BigDecimal amount = parseFrenchAmount(amountStr);
                if (amount == null) {
                    continue;
                }

                // Determine debit/credit (simplified)
                String descriptionUpper = description.toUpperCase();
                boolean isDebit = descriptionUpper.contains("PAIEMENT") || 
                                 descriptionUpper.contains("COMMISSION");
                
                if (isDebit) {
                    amount = amount.negate();
                }

                NormalizedTransaction.TransactionType type = isDebit 
                    ? NormalizedTransaction.TransactionType.DEBIT 
                    : NormalizedTransaction.TransactionType.CREDIT;

                NormalizedTransaction transaction = NormalizedTransaction.builder()
                    .accountNumber(accountNumber)
                    .accountType("CHECKING")
                    .transactionDate(operDate)
                    .valueDate(valeurDate != null ? valeurDate : operDate)
                    .amount(amount)
                    .currency(currency)
                    .description(line)
                    .bankReference(null)
                    .category(null)
                    .balanceAfter(null)
                    .type(type)
                    .build();

                transactions.add(transaction);

            } catch (Exception e) {
                log.debug("[DEBUG][CIH] Error parsing line {}: {}", i + 1, line, e);
            }
        }

        return transactions;
    }

    private LocalDate parseDate(String dateStr, int year) {
        try {
            String[] parts = dateStr.split("/");
            if (parts.length == 2) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                return LocalDate.of(year, month, day);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private java.math.BigDecimal parseFrenchAmount(String amountStr) {
        try {
            String cleaned = amountStr.trim().replaceAll("\\s+", "").replace(",", ".");
            return new java.math.BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }
}

