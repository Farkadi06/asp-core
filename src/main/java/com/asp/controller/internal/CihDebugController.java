package com.asp.controller.internal;

import com.asp.domain.Ingestion;
import com.asp.domain.IngestionRepository;
import com.asp.domain.normalized.NormalizedTransaction;
import com.asp.parsing.extraction.PdfExtractionException;
import com.asp.parsing.extraction.PdfTextExtractor;
import com.asp.parsing.extraction.TextExtractionResult;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Debug endpoint to inspect CIH parsing with page awareness.
 */
@RestController
@RequestMapping("/internal/debug")
@RequiredArgsConstructor
@Slf4j
public class CihDebugController {

    private static final Pattern TRANSACTION_LINE_PATTERN = Pattern.compile(
        "^(\\d{2}/\\d{2})(\\d{2}/\\d{2})\\s+(.*?)(\\d[\\d\\s\\.,]+)$"
            + "|"
            + "^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})\\s+(.*?)(\\d[\\d\\s\\.,]+)$"
    );
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^\\d{1,4}/\\d{1,4}$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String MOVEMENT_TOTAL_PREFIX = "REPORT TOTAL MOUVEMENT";

    private final IngestionRepository ingestionRepository;
    private final PdfTextExtractor pdfTextExtractor;

    @GetMapping("/cih-detect")
    public ResponseEntity<Map<String, Object>> debug(@RequestParam UUID ingestionId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context not available");
        }

        Ingestion ingestion = ingestionRepository.findByIdAndTenantId(ingestionId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Ingestion not found: " + ingestionId));

        if (ingestion.getStoragePath() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingestion has no storage path");
        }

        Map<String, Object> response = new HashMap<>();

        try {
            TextExtractionResult extraction = pdfTextExtractor.extract(
                tenantId, ingestionId, ingestion.getStoragePath());

            String extractedText = extraction.getText();
            List<String> pages = extraction.getPages() == null || extraction.getPages().isEmpty()
                ? List.of(extractedText)
                : extraction.getPages();

            response.put("status", "success");
            response.put("pageCount", pages.size());
            response.put("extractedTextLength", extractedText != null ? extractedText.length() : 0);

            List<Map<String, Object>> pageSummaries = new ArrayList<>();
            List<NormalizedTransaction> allTransactions = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (int i = 0; i < pages.size(); i++) {
                String pageText = pages.get(i) == null ? "" : pages.get(i);
                PageType pageType = determinePageType(i, pages.size());
                PageDebugResult result = debugParsePage(pageText, pageType, i + 1);

                allTransactions.addAll(result.transactions());
                warnings.addAll(result.warnings());

                Map<String, Object> pageInfo = new HashMap<>();
                pageInfo.put("pageNumber", i + 1);
                pageInfo.put("pageType", pageType);
                pageInfo.put("lineCount", result.lineCount());
                pageInfo.put("matchedLines", result.matchedLines());
                pageInfo.put("skippedLines", result.skippedLines());
                pageInfo.put("parsedTransactions", result.transactions().size());
                pageSummaries.add(pageInfo);
            }

            response.put("pageSummaries", pageSummaries);
            response.put("parsedTransactions", allTransactions.stream()
                .map(tx -> Map.of(
                    "date", tx.getTransactionDate(),
                    "valueDate", tx.getValueDate(),
                    "amount", tx.getAmount(),
                    "type", tx.getType(),
                    "description", tx.getDescription()))
                .collect(Collectors.toList()));
            response.put("transactionCount", allTransactions.size());
            response.put("warnings", warnings);

            return ResponseEntity.ok(response);

        } catch (PdfExtractionException ex) {
            log.error("[DEBUG][CIH] PDF extraction error", ex);
            response.put("status", "error");
            response.put("error", "PDF extraction failed: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception ex) {
            log.error("[DEBUG][CIH] Unexpected error", ex);
            response.put("status", "error");
            response.put("error", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private PageDebugResult debugParsePage(String pageText, PageType pageType, int pageNumber) {
        List<Map<String, Object>> matchedLines = new ArrayList<>();
        List<Map<String, Object>> skippedLines = new ArrayList<>();
        List<NormalizedTransaction> transactions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (pageText == null) {
            return new PageDebugResult(0, matchedLines, skippedLines, transactions, warnings);
        }

        String[] rawLines = pageText.split("\\r?\\n");
        boolean readyForTransactions = pageType != PageType.FIRST;

        for (int i = 0; i < rawLines.length; i++) {
            String original = rawLines[i];
            String line = original != null ? original.trim() : "";
            if (line.isEmpty()) {
                continue;
            }
            String upper = line.toUpperCase();

            if (pageType == PageType.FIRST && !readyForTransactions) {
                if (upper.contains("SOLDE DEPART AU")) {
                    readyForTransactions = true;
                }
                continue;
            }

            if (upper.contains("DATES / OPER / VALEUR")) {
                continue;
            }
            if (PAGE_NUMBER_PATTERN.matcher(line).matches()) {
                continue;
            }
            if (upper.startsWith(MOVEMENT_TOTAL_PREFIX)) {
                continue;
            }
            if (upper.contains("SOLDE FIN AU")) {
                continue;
            }

            Matcher matcher = TRANSACTION_LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                skippedLines.add(lineInfo(pageNumber, i + 1, line, "NO_MATCH"));
                continue;
            }

            String operDateStr;
            String valeurDateStr;
            String description;
            String amountStr;

            if (matcher.group(1) != null) {
                operDateStr = matcher.group(1);
                valeurDateStr = matcher.group(2);
                description = matcher.group(3);
                amountStr = matcher.group(4);
            } else {
                operDateStr = matcher.group(5);
                valeurDateStr = matcher.group(6);
                description = matcher.group(7);
                amountStr = matcher.group(8);
            }

            Map<String, Object> matchInfo = new HashMap<>();
            matchInfo.put("page", pageNumber);
            matchInfo.put("line", i + 1);
            matchInfo.put("operDate", operDateStr);
            matchInfo.put("valDate", valeurDateStr);
            matchInfo.put("description", description);
            matchInfo.put("amountRaw", amountStr);
            matchedLines.add(matchInfo);

            try {
                LocalDate transactionDate = parseDate(operDateStr);
                LocalDate valueDate = parseDate(valeurDateStr);
                BigDecimal amount = parseFrenchAmount(amountStr);
                if (transactionDate == null || amount == null) {
                    warnings.add(String.format("Page %d line %d skipped (date/amount parse failure)",
                        pageNumber, i + 1));
                    continue;
                }

                BigDecimal signedAmount = determineSignedAmount(description, amount);
                NormalizedTransaction.TransactionType type = signedAmount.signum() >= 0
                    ? NormalizedTransaction.TransactionType.CREDIT
                    : NormalizedTransaction.TransactionType.DEBIT;

                transactions.add(NormalizedTransaction.builder()
                    .accountNumber("UNKNOWN")
                    .accountType("CHECKING")
                    .transactionDate(transactionDate)
                    .valueDate(valueDate != null ? valueDate : transactionDate)
                    .amount(signedAmount)
                    .currency("MAD")
                    .description(original.trim())
                    .type(type)
                    .build());

            } catch (Exception ex) {
                warnings.add(String.format("Page %d line %d parse error: %s",
                    pageNumber, i + 1, ex.getMessage()));
            }
        }

        return new PageDebugResult(rawLines.length, matchedLines, skippedLines, transactions, warnings);
    }

    private Map<String, Object> lineInfo(int page, int line, String content, String reason) {
        Map<String, Object> map = new HashMap<>();
        map.put("page", page);
        map.put("line", line);
        map.put("content", content);
        map.put("reason", reason);
        return map;
    }

    private LocalDate parseDate(String ddMM) {
        try {
            return LocalDate.parse(ddMM + "/" + LocalDate.now().getYear(), DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal parseFrenchAmount(String amountStr) {
        try {
            return new BigDecimal(amountStr.trim().replaceAll("\\s+", "").replace(",", "."));
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal determineSignedAmount(String description, BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        String upper = description != null ? description.toUpperCase() : "";

        if (upper.contains("VIREMENT RECU") || upper.contains("VIRT RECU") || upper.contains("VERSEMENT")) {
            return amount.abs();
        }
        return amount.negate();
    }

    private PageType determinePageType(int index, int total) {
        if (index == 0) {
            return PageType.FIRST;
        }
        if (index == total - 1) {
            return PageType.LAST;
        }
        return PageType.MIDDLE;
    }

    private enum PageType {
        FIRST,
        MIDDLE,
        LAST
    }

    private record PageDebugResult(
        int lineCount,
        List<Map<String, Object>> matchedLines,
        List<Map<String, Object>> skippedLines,
        List<NormalizedTransaction> transactions,
        List<String> warnings
    ) {}
}

