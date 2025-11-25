package com.asp.parsing.parser.impl;

import com.asp.domain.normalized.*;
import com.asp.parsing.detection.BankDetectionResult;
import com.asp.parsing.parser.*;
import com.asp.parsing.extraction.TextExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for CIH (Crédit Immobilier et Hôtelier) PDF statements.
 * Parses real-world CIH bank statement format with exact regex patterns.
 */
@Component
@Slf4j
public class CihPdfParser implements ParserStrategy {

    // RIB pattern: 3 digits + 3 digits + 16 digits + 2 digits
    private static final Pattern RIB_PATTERN = Pattern.compile("([0-9]{3})\\s+([0-9]{3})\\s+([0-9]{16})\\s+([0-9]{2})");
    
    // Opening balance: SOLDE DEPART AU : dd/MM/yyyy amount
    private static final Pattern OPENING_BALANCE_PATTERN = Pattern.compile(
        "SOLDE DEPART AU\\s*:\\s*(\\d{2}/\\d{2}/\\d{4})\\s+([0-9][0-9 \\.,]+)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Transaction line patterns (merged & spaced dates) ending with French number
    private static final Pattern TRANSACTION_LINE_PATTERN = Pattern.compile(
        // Case 1 — merged dates: "01/1001/10 Description 99,00"
        "^(\\d{2}/\\d{2})(\\d{2}/\\d{2})\\s+(.*?)(\\d[\\d\\s\\.,]+)$"
        + "|"
        // Case 2 — spaced dates: "01/10 01/10 Description 99,00"
        + "^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})\\s+(.*?)(\\d[\\d\\s\\.,]+)$"
    );
    
    // Debit keywords (negative amounts)
    private static final Set<String> DEBIT_KEYWORDS = Set.of(
        "COMMISSION", "PAIEMENT", "RECHARGE", "VIREMENT EMIS"
    );
    
    // Credit keywords (positive amounts)
    private static final Set<String> CREDIT_KEYWORDS = Set.of(
        "VIREMENT RECU", "VIRT RECU", "VERSEMENT"
    );
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public ParserResult parse(TextExtractionResult extractionResult, BankDetectionResult detection, IngestionContext context) {
        log.info("[CIH][INGESTION:{}] Starting CIH statement parsing", context.getIngestionId());

        if (extractionResult == null || extractionResult.getText() == null || extractionResult.getText().trim().isEmpty()) {
            log.warn("[CIH][INGESTION:{}] Empty text extracted from PDF", context.getIngestionId());
            return createEmptyResult("Empty text extracted from PDF");
        }

        String extractedText = extractionResult.getText();
        List<String> pages = extractionResult.getPages();
        if (pages == null || pages.isEmpty()) {
            pages = List.of(extractedText);
        }

        log.info("[CIH][INGESTION:{}] Extracted text length: {} characters across {} pages",
            context.getIngestionId(), extractedText.length(), pages.size());

        List<ParserWarning> warnings = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("parser", "CihPdfParser");
        metadata.put("version", "3.0.0");
        metadata.put("textLength", extractedText.length());
        metadata.put("pageCount", pages.size());

        try {
            String currency = parseCurrency(extractedText);
            String accountNumber = null;
            LocalDate openingDate = null;
            BigDecimal openingBalance = null;
            List<NormalizedTransaction> transactions = new ArrayList<>();

            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                PageType pageType = determinePageType(pageIndex, pages.size());
                String pageText = pages.get(pageIndex) != null ? pages.get(pageIndex) : "";
                log.info("[CIH][INGESTION:{}] Processing page {} of {} ({})",
                    context.getIngestionId(), pageIndex + 1, pages.size(), pageType);

                if (pageType == PageType.FIRST) {
                    if (accountNumber == null) {
                        accountNumber = parseAccountNumber(pageText, warnings);
                    }
                    if (openingDate == null || openingBalance == null) {
                        OpeningBalanceResult openingResult = parseOpeningBalance(pageText, warnings, context);
                        if (openingResult != null) {
                            openingDate = openingResult.date();
                            openingBalance = openingResult.balance();
                        }
                    }
                }

                List<NormalizedTransaction> pageTransactions = parseTransactionsForPage(
                    pageText,
                    pageType,
                    accountNumber,
                    currency,
                    openingDate,
                    warnings,
                    context,
                    pageIndex + 1
                );
                transactions.addAll(pageTransactions);
            }

            if (accountNumber == null) {
                accountNumber = "UNKNOWN";
            }

            if (transactions.isEmpty()) {
                log.warn("[CIH][INGESTION:{}] WARNING: ParserResult.transactions is EMPTY! This may indicate a parsing issue.",
                    context.getIngestionId());
            }

            NormalizedAccount account = NormalizedAccount.builder()
                .accountNumber(accountNumber)
                .accountType("CHECKING")
                .currency(currency)
                .bankIdentifier("cih")
                .build();

            NormalizedAccountSnapshot snapshot = null;
            if (openingDate != null && openingBalance != null) {
                snapshot = NormalizedAccountSnapshot.builder()
                    .accountNumber(accountNumber)
                    .accountType("CHECKING")
                    .snapshotDate(openingDate)
                    .openingBalance(openingBalance)
                    .closingBalance(null)
                    .currency(currency)
                    .totalDebits(null)
                    .totalCredits(null)
                    .transactionCount(transactions.size())
                    .build();
            }

            DateRange statementPeriod = null;
            if (openingDate != null && !transactions.isEmpty()) {
                LocalDate endDate = transactions.stream()
                    .map(NormalizedTransaction::getTransactionDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(openingDate);
                statementPeriod = DateRange.builder()
                    .startDate(openingDate)
                    .endDate(endDate)
                    .build();
            }

            boolean isComplete = accountNumber != null
                && !"UNKNOWN".equals(accountNumber)
                && openingDate != null
                && openingBalance != null;

            metadata.put("pagesProcessed", pages.size());
            metadata.put("transactionsParsed", transactions.size());

            return ParserResult.builder()
                .accounts(List.of(account))
                .transactions(transactions)
                .snapshots(snapshot != null ? List.of(snapshot) : new ArrayList<>())
                .statementPeriod(statementPeriod)
                .metadata(metadata)
                .warnings(warnings)
                .isComplete(isComplete)
                .build();

        } catch (Exception e) {
            log.error("[CIH][INGESTION:{}] Error parsing CIH statement", context.getIngestionId(), e);
            warnings.add(ParserWarning.builder()
                .code("PARSING_ERROR")
                .message("Unexpected error during parsing: " + e.getMessage())
                .build());
            return createEmptyResult("Error parsing CIH statement: " + e.getMessage());
        }
    }

    private String parseCurrency(String text) {
        if (text != null && text.contains("DIRHAM MAROCAIN")) {
            return "MAD";
        }
        return "MAD";  // Default to MAD for CIH
    }

    private String parseAccountNumber(String text, List<ParserWarning> warnings) {
        Matcher matcher = RIB_PATTERN.matcher(text);
        if (matcher.find()) {
            String accountNumber = matcher.group(3).replaceAll("\\s+", "");
            log.debug("Found account number: {}", accountNumber);
            return accountNumber;
        }
        warnings.add(ParserWarning.builder()
            .code("ACCOUNT_NUMBER_NOT_FOUND")
            .message("Could not find account number in RIB format")
            .field("accountNumber")
            .build());
        log.warn("Could not find account number in RIB format");
        return "UNKNOWN";  // Fallback
    }

    private List<NormalizedTransaction> parseTransactionsForPage(
            String pageText,
            PageType pageType,
            String accountNumber,
            String currency,
            LocalDate openingDate,
            List<ParserWarning> warnings,
            IngestionContext context,
            int pageNumber) {

        List<NormalizedTransaction> transactions = new ArrayList<>();
        if (pageText == null || pageText.trim().isEmpty()) {
            log.warn("[CIH][INGESTION:{}] Page {} is empty, skipping", context.getIngestionId(), pageNumber);
            return transactions;
        }

        String[] lines = pageText.split("\\r?\\n");
        int year = openingDate != null ? openingDate.getYear() : LocalDate.now().getYear();
        boolean readyForTransactions = pageType != PageType.FIRST;
        int parsedCount = 0;
        int skippedCount = 0;

        log.info("[CIH][INGESTION:{}] Parsing page {} ({}) with {} lines",
            context.getIngestionId(), pageNumber, pageType, lines.length);

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i];
            String line = rawLine != null ? rawLine.trim() : "";

            if (line.isEmpty()) {
                continue;
            }

            String upperLine = line.toUpperCase();

            if (pageType == PageType.FIRST && !readyForTransactions) {
                if (upperLine.contains("SOLDE DEPART AU")) {
                    readyForTransactions = true;
                }
                continue;
            }

            if (upperLine.contains("DATES / OPER / VALEUR")) {
                continue;
            }

            if (PAGE_NUMBER_PATTERN.matcher(line).matches()) {
                continue;
            }

            if (upperLine.startsWith(MOVEMENT_TOTAL_PREFIX)) {
                log.debug("[CIH][page {} line {}] Skipping movement total line", pageNumber, i + 1);
                continue;
            }

            if (upperLine.contains("SOLDE FIN AU")) {
                // Ignore closing balance summaries for now
                continue;
            }

            Matcher lineMatcher = TRANSACTION_LINE_PATTERN.matcher(line);
            if (!lineMatcher.matches()) {
                log.debug("[CIH][page {} line {}][skip] No transaction match: \"{}\"",
                    pageNumber, i + 1, line);
                warnings.add(ParserWarning.builder()
                    .code("NO_TX_MATCH")
                    .message("Line does not match transaction pattern: " + line)
                    .field("transactionLine")
                    .build());
                skippedCount++;
                continue;
            }

            String operDateStr;
            String valeurDateStr;
            String descriptionPart;
            String amountStr;

            if (lineMatcher.group(1) != null) {
                // Merged format
                operDateStr = lineMatcher.group(1);
                valeurDateStr = lineMatcher.group(2);
                descriptionPart = lineMatcher.group(3);
                amountStr = lineMatcher.group(4);
            } else {
                // Spaced format
                operDateStr = lineMatcher.group(5);
                valeurDateStr = lineMatcher.group(6);
                descriptionPart = lineMatcher.group(7);
                amountStr = lineMatcher.group(8);
            }

            try {
                LocalDate operDate = parseTransactionDate(operDateStr, year, warnings);
                LocalDate valeurDate = parseTransactionDate(valeurDateStr, year, warnings);

                if (operDate == null) {
                    warnings.add(ParserWarning.builder()
                        .code("INVALID_DATE")
                        .message("Failed to parse operation date: " + operDateStr)
                        .field("date")
                        .build());
                    skippedCount++;
                    continue;
                }

                String description = descriptionPart != null ? descriptionPart.trim() : "";
                log.debug("[CIH][page {} line {}][amount] desc=\"{}\" amountStr=\"{}\"",
                    pageNumber, i + 1, description, amountStr);

                BigDecimal amount = parseFrenchAmount(amountStr);
                if (amount == null) {
                    warnings.add(ParserWarning.builder()
                        .code("INVALID_AMOUNT")
                        .message("Could not parse amount: " + amountStr)
                        .field("amount")
                        .build());
                    skippedCount++;
                    continue;
                }

                BigDecimal finalAmount = determineSignedAmount(description, amount);
                if (finalAmount == null) {
                    skippedCount++;
                    continue;
                }
                NormalizedTransaction.TransactionType type = finalAmount.signum() >= 0
                    ? NormalizedTransaction.TransactionType.CREDIT
                    : NormalizedTransaction.TransactionType.DEBIT;

                NormalizedTransaction transaction = NormalizedTransaction.builder()
                    .accountNumber(accountNumber)
                    .accountType("CHECKING")
                    .transactionDate(operDate)
                    .valueDate(valeurDate != null ? valeurDate : operDate)
                    .amount(finalAmount)
                    .currency(currency)
                    .description(rawLine.trim())
                    .bankReference(null)
                    .category(null)
                    .balanceAfter(null)
                    .type(type)
                    .build();

                transactions.add(transaction);
                parsedCount++;

                log.info("[CIH][page {} line {}][tx] Parsed -> date={}, amount={}, description=\"{}\", type={}",
                    pageNumber, i + 1, operDate, finalAmount, description, type);

            } catch (Exception e) {
                log.error("[CIH][page {} line {}][ERROR] Exception parsing transaction line: \"{}\"",
                    pageNumber, i + 1, line, e);
                warnings.add(ParserWarning.builder()
                    .code("TRANSACTION_PARSE_ERROR")
                    .message("Failed to parse transaction: " + e.getMessage())
                    .build());
                skippedCount++;
            }
        }

        log.info("[CIH][INGESTION:{}] Page {} parsed: {} transactions, {} skipped",
            context.getIngestionId(), pageNumber, parsedCount, skippedCount);
        return transactions;
    }

    private LocalDate parseTransactionDate(String dateStr, int year, List<ParserWarning> warnings) {
        try {
            String[] parts = dateStr.split("/");
            if (parts.length == 2) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                return LocalDate.of(year, month, day);
            }
        } catch (Exception e) {
            log.warn("Failed to parse transaction date: {}", dateStr, e);
        }
        return null;
    }

    /**
     * Parses French-formatted number: removes spaces, replaces comma with dot.
     * Example: "15 000,00" -> 15000.00, "41,00" -> 41.00
     */
    private BigDecimal parseFrenchAmount(String amountStr) {
        try {
            // Remove all spaces and replace comma with dot
            String cleaned = amountStr.trim().replaceAll("\\s+", "").replace(",", ".");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse French amount: {}", amountStr, e);
            return null;
        }
    }

    private BigDecimal determineSignedAmount(String description, BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        String upperDescription = description != null ? description.toUpperCase() : "";

        for (String keyword : CREDIT_KEYWORDS) {
            if (upperDescription.contains(keyword)) {
                return amount.abs();
            }
        }

        for (String keyword : DEBIT_KEYWORDS) {
            if (upperDescription.contains(keyword)) {
                return amount.negate();
            }
        }

        // Default to negative (most transactions are debits)
        return amount.negate();
    }

    private enum PageType {
        FIRST,
        MIDDLE,
        LAST
    }

    private record OpeningBalanceResult(LocalDate date, BigDecimal balance) {}

    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^\\d{1,4}/\\d{1,4}$");
    private static final String MOVEMENT_TOTAL_PREFIX = "REPORT TOTAL MOUVEMENT";

    private ParserResult createEmptyResult(String reason) {
        return ParserResult.builder()
            .accounts(new ArrayList<>())
            .transactions(new ArrayList<>())
            .snapshots(new ArrayList<>())
            .warnings(List.of(ParserWarning.builder()
                .code("PARSING_FAILED")
                .message(reason)
                .build()))
            .isComplete(false)
            .build();
    }

    private PageType determinePageType(int index, int totalPages) {
        if (index == 0) {
            return PageType.FIRST;
        }
        if (index == totalPages - 1) {
            return PageType.LAST;
        }
        return PageType.MIDDLE;
    }

    private OpeningBalanceResult parseOpeningBalance(String pageText, List<ParserWarning> warnings, IngestionContext context) {
        Matcher openingMatcher = OPENING_BALANCE_PATTERN.matcher(pageText != null ? pageText : "");
        if (openingMatcher.find()) {
            try {
                String dateStr = openingMatcher.group(1);
                LocalDate openingDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                String amountStr = openingMatcher.group(2).trim();
                BigDecimal openingBalance = parseFrenchAmount(amountStr);
                log.debug("[CIH][INGESTION:{}] Found opening balance: {} on {}",
                    context.getIngestionId(), openingBalance, openingDate);
                return new OpeningBalanceResult(openingDate, openingBalance);
            } catch (DateTimeParseException e) {
                warnings.add(ParserWarning.builder()
                    .code("INVALID_OPENING_DATE")
                    .message("Failed to parse opening balance date: " + e.getMessage())
                    .field("openingDate")
                    .build());
                log.warn("[CIH][INGESTION:{}] Failed to parse opening balance date", context.getIngestionId(), e);
            } catch (NumberFormatException e) {
                warnings.add(ParserWarning.builder()
                    .code("INVALID_OPENING_BALANCE")
                    .message("Failed to parse opening balance amount: " + e.getMessage())
                    .field("openingBalance")
                    .build());
                log.warn("[CIH][INGESTION:{}] Failed to parse opening balance amount", context.getIngestionId(), e);
            }
        } else {
            warnings.add(ParserWarning.builder()
                .code("OPENING_BALANCE_NOT_FOUND")
                .message("Could not find opening balance line on first page")
                .field("openingBalance")
                .build());
            log.warn("[CIH][INGESTION:{}] Could not find opening balance line on first page", context.getIngestionId());
        }
        return null;
    }

    @Override
    public boolean canHandle(String bankIdentifier) {
        return bankIdentifier != null && bankIdentifier.equalsIgnoreCase("cih");
    }

    @Override
    public String getParserName() {
        return "CIH PDF Parser";
    }

    @Override
    public Set<ParserCapabilities> getCapabilities() {
        return Set.of(
            ParserCapabilities.ACCOUNT_EXTRACTION,
            ParserCapabilities.TRANSACTION_EXTRACTION,
            ParserCapabilities.BALANCE_EXTRACTION,
            ParserCapabilities.DATE_RANGE_EXTRACTION
        );
    }
}
