package com.asp.parsing.parser.impl;

import com.asp.domain.normalized.*;
import com.asp.parsing.detection.BankDetectionResult;
import com.asp.parsing.parser.*;
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
 */
@Component
@Slf4j
public class CihPdfParser implements ParserStrategy {

    private static final Pattern RIB_PATTERN = Pattern.compile("([0-9]{3})\\s+([0-9]{3})\\s+([0-9]{16})\\s+([0-9]{2})");
    private static final Pattern OPENING_BALANCE_PATTERN = Pattern.compile(
        "SOLDE DEPART AU\\s*:\\s*(\\d{2}/\\d{2}/\\d{4})\\s+(?:.*?\\s+)?([0-9 ]*,\\d{2})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
        "(\\d{2}/\\d{2})\\s+" +           // Date 1 (day/month)
        "(\\d{2}/\\d{2})\\s+" +           // Date 2 (day/month)
        "(\\d{2}/\\d{2})\\s+" +           // Date 3 (day/month)
        "(?:(\\d[\\d ]*,\\d{2}))?\\s*" +  // Debit (optional)
        "(?:(\\d[\\d ]*,\\d{2}))?\\s+" +  // Credit (optional)
        "(.+)"                             // Description
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public ParserResult parse(String extractedText, BankDetectionResult detection, IngestionContext context) {
        log.info("[INGESTION:{}] Parsing CIH statement", context.getIngestionId());

        if (extractedText == null || extractedText.trim().isEmpty()) {
            log.warn("[INGESTION:{}] Empty text extracted from PDF", context.getIngestionId());
            return createEmptyResult("Empty text extracted from PDF");
        }

        List<ParserWarning> warnings = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("parser", "CihPdfParser");
        metadata.put("version", "1.0.0");
        metadata.put("textLength", extractedText.length());

        try {
            // 1. Parse metadata
            String currency = parseCurrency(extractedText);
            String accountNumber = parseAccountNumber(extractedText, warnings);
            LocalDate openingDate = null;
            BigDecimal openingBalance = null;

            Matcher openingMatcher = OPENING_BALANCE_PATTERN.matcher(extractedText);
            if (openingMatcher.find()) {
                try {
                    String dateStr = openingMatcher.group(1);
                    openingDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                    String amountStr = openingMatcher.group(2).trim().replaceAll("\\s+", "").replace(",", ".");
                    openingBalance = new BigDecimal(amountStr);
                } catch (DateTimeParseException e) {
                    warnings.add(ParserWarning.builder()
                        .code("INVALID_OPENING_DATE")
                        .message("Failed to parse opening balance date: " + e.getMessage())
                        .field("openingDate")
                        .build());
                    log.warn("[INGESTION:{}] Failed to parse opening balance date", context.getIngestionId(), e);
                } catch (NumberFormatException e) {
                    warnings.add(ParserWarning.builder()
                        .code("INVALID_OPENING_BALANCE")
                        .message("Failed to parse opening balance amount: " + e.getMessage())
                        .field("openingBalance")
                        .build());
                    log.warn("[INGESTION:{}] Failed to parse opening balance amount", context.getIngestionId(), e);
                }
            } else {
                warnings.add(ParserWarning.builder()
                    .code("OPENING_BALANCE_NOT_FOUND")
                    .message("Could not find opening balance line")
                    .field("openingBalance")
                    .build());
                log.warn("[INGESTION:{}] Could not find opening balance line", context.getIngestionId());
            }

            // 2. Parse transactions
            List<NormalizedTransaction> transactions = parseTransactions(
                extractedText,
                accountNumber,
                currency,
                openingDate,
                warnings,
                context
            );

            // 3. Build account
            NormalizedAccount account = NormalizedAccount.builder()
                .accountNumber(accountNumber)
                .accountType("CHECKING")
                .currency(currency)
                .bankIdentifier("cih")
                .build();

            // 4. Build snapshot
            NormalizedAccountSnapshot snapshot = null;
            if (openingDate != null && openingBalance != null) {
                snapshot = NormalizedAccountSnapshot.builder()
                    .accountNumber(accountNumber)
                    .accountType("CHECKING")
                    .snapshotDate(openingDate)
                    .openingBalance(openingBalance)
                    .closingBalance(null)  // Not available in CIH format
                    .currency(currency)
                    .totalDebits(null)
                    .totalCredits(null)
                    .transactionCount(transactions.size())
                    .build();
            }

            // 5. Build statement period
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

            boolean isComplete = accountNumber != null && openingDate != null && openingBalance != null;

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
            log.error("[INGESTION:{}] Error parsing CIH statement", context.getIngestionId(), e);
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

    private List<NormalizedTransaction> parseTransactions(
            String text,
            String accountNumber,
            String currency,
            LocalDate openingDate,
            List<ParserWarning> warnings,
            IngestionContext context) {
        
        List<NormalizedTransaction> transactions = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        
        int year = openingDate != null ? openingDate.getYear() : LocalDate.now().getYear();
        int parsedCount = 0;
        int skippedCount = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher matcher = TRANSACTION_PATTERN.matcher(line);
            if (matcher.matches()) {
                try {
                    String date1Str = matcher.group(1);
                    String date2Str = matcher.group(2);
                    String date3Str = matcher.group(3);
                    String debitStr = matcher.group(4);
                    String creditStr = matcher.group(5);
                    String extractedDescription = matcher.group(6).trim();
                    String fullOriginalLine = line;  // Store full line for rawDescription

                    // Use the first date (transaction date)
                    LocalDate transactionDate = parseTransactionDate(date1Str, year, warnings);
                    if (transactionDate == null) {
                        skippedCount++;
                        continue;
                    }

                    // Parse amount
                    BigDecimal amount = null;
                    NormalizedTransaction.TransactionType type = null;
                    
                    if (debitStr != null && !debitStr.trim().isEmpty()) {
                        amount = parseAmount(debitStr);
                        if (amount != null) {
                            amount = amount.negate();  // Debit is negative
                            type = NormalizedTransaction.TransactionType.DEBIT;
                        }
                    } else if (creditStr != null && !creditStr.trim().isEmpty()) {
                        amount = parseAmount(creditStr);
                        if (amount != null) {
                            type = NormalizedTransaction.TransactionType.CREDIT;
                        }
                    }

                    if (amount == null) {
                        warnings.add(ParserWarning.builder()
                            .code("MISSING_AMOUNT")
                            .message("Transaction has no debit or credit amount: " + line)
                            .field("amount")
                            .build());
                        skippedCount++;
                        continue;
                    }

                    NormalizedTransaction transaction = NormalizedTransaction.builder()
                        .accountNumber(accountNumber)
                        .accountType("CHECKING")
                        .transactionDate(transactionDate)
                        .valueDate(transactionDate)  // Use same date for value date
                        .amount(amount)
                        .currency(currency)
                        .description(fullOriginalLine)  // Full original line (writer will use as rawDescription)
                        .bankReference(null)
                        .category(null)
                        .balanceAfter(null)
                        .type(type)
                        .build();

                    transactions.add(transaction);
                    parsedCount++;

                } catch (Exception e) {
                    log.warn("[INGESTION:{}] Error parsing transaction line: {}", context.getIngestionId(), line, e);
                    warnings.add(ParserWarning.builder()
                        .code("TRANSACTION_PARSE_ERROR")
                        .message("Failed to parse transaction: " + e.getMessage())
                        .build());
                    skippedCount++;
                }
            }
        }

        log.info("[INGESTION:{}] Parsed {} transactions, skipped {} lines", 
            context.getIngestionId(), parsedCount, skippedCount);
        
        if (skippedCount > 0) {
            warnings.add(ParserWarning.builder()
                .code("SKIPPED_TRANSACTIONS")
                .message("Skipped " + skippedCount + " lines that did not match transaction pattern")
                .build());
        }

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

    private BigDecimal parseAmount(String amountStr) {
        try {
            // Remove spaces and replace comma with dot
            String cleaned = amountStr.trim().replaceAll("\\s+", "").replace(",", ".");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse amount: {}", amountStr, e);
            return null;
        }
    }

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
