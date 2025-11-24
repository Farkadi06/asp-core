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
    
    // Transaction line: two dates (dd/MM) followed by description+amount
    private static final Pattern TRANSACTION_LINE_PATTERN = Pattern.compile(
        "^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})\\s+(.*)$"
    );
    
    // Description + amount: description followed by amount at end
    private static final Pattern DESCRIPTION_AMOUNT_PATTERN = Pattern.compile(
        "(.+?)\\s+([0-9][0-9 \\.,]+)$"
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
    public ParserResult parse(String extractedText, BankDetectionResult detection, IngestionContext context) {
        log.info("[CIH][INGESTION:{}] Starting CIH statement parsing", context.getIngestionId());

        if (extractedText == null || extractedText.trim().isEmpty()) {
            log.warn("[CIH][INGESTION:{}] Empty text extracted from PDF", context.getIngestionId());
            return createEmptyResult("Empty text extracted from PDF");
        }

        log.info("[CIH][INGESTION:{}] Extracted text length: {} characters", 
            context.getIngestionId(), extractedText.length());

        List<ParserWarning> warnings = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("parser", "CihPdfParser");
        metadata.put("version", "2.0.0");
        metadata.put("textLength", extractedText.length());

        try {
            // 1. Parse metadata
            String currency = parseCurrency(extractedText);
            String accountNumber = parseAccountNumber(extractedText, warnings);
            
            // 2. Parse opening balance
            LocalDate openingDate = null;
            BigDecimal openingBalance = null;
            
            Matcher openingMatcher = OPENING_BALANCE_PATTERN.matcher(extractedText);
            if (openingMatcher.find()) {
                try {
                    String dateStr = openingMatcher.group(1);
                    openingDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                    String amountStr = openingMatcher.group(2).trim();
                    openingBalance = parseFrenchAmount(amountStr);
                    log.debug("[INGESTION:{}] Found opening balance: {} on {}", 
                        context.getIngestionId(), openingBalance, openingDate);
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

            // 3. Parse transactions
            log.info("[CIH][INGESTION:{}] Starting transaction parsing...", context.getIngestionId());
            List<NormalizedTransaction> transactions = parseTransactions(
                extractedText,
                accountNumber,
                currency,
                openingDate,
                warnings,
                context
            );
            log.info("[CIH][INGESTION:{}] Transaction parsing complete: {} transactions found", 
                context.getIngestionId(), transactions.size());

            if (transactions.isEmpty()) {
                log.warn("[CIH][INGESTION:{}] WARNING: ParserResult.transactions is EMPTY! This may indicate a parsing issue.", 
                    context.getIngestionId());
            }

            // 4. Build account
            NormalizedAccount account = NormalizedAccount.builder()
                .accountNumber(accountNumber)
                .accountType("CHECKING")
                .currency(currency)
                .bankIdentifier("cih")
                .build();
            log.info("[CIH][INGESTION:{}] Account created: accountNumber={}", context.getIngestionId(), accountNumber);

            // 5. Build snapshot
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
                log.info("[CIH][INGESTION:{}] Snapshot created: date={}, balance={}", 
                    context.getIngestionId(), openingDate, openingBalance);
            } else {
                log.warn("[CIH][INGESTION:{}] No snapshot created (missing opening date or balance)", 
                    context.getIngestionId());
            }

            // 6. Build statement period
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

            boolean isComplete = accountNumber != null && !accountNumber.equals("UNKNOWN") 
                && openingDate != null && openingBalance != null;

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
        int lineIndex = 0;

        log.info("[CIH][INGESTION:{}] Starting transaction parsing: {} total lines, year={}", 
            context.getIngestionId(), lines.length, year);

        for (String line : lines) {
            lineIndex++;
            String originalLine = line;
            line = line.trim();
            
            // Log every line being scanned
            log.debug("[CIH][line {}] RAW: \"{}\"", lineIndex, originalLine);
            
            if (line.isEmpty()) {
                log.debug("[CIH][line {}] SKIP: Empty line", lineIndex);
                continue;
            }

            // Step 1: Match transaction line pattern (two dates + rest)
            Matcher lineMatcher = TRANSACTION_LINE_PATTERN.matcher(line);
            if (!lineMatcher.matches()) {
                // Not a transaction line, skip with warning
                log.debug("[CIH][line {}][skip] TRANSACTION_LINE_PATTERN did not match: \"{}\"", lineIndex, line);
                warnings.add(ParserWarning.builder()
                    .code("NO_TX_MATCH")
                    .message("Line does not match transaction pattern (expected: dd/MM dd/MM ...): " + line)
                    .field("transactionLine")
                    .build());
                skippedCount++;
                continue;
            }

            // Log successful match
            String operDateStr = lineMatcher.group(1);
            String valeurDateStr = lineMatcher.group(2);
            String restOfLine = lineMatcher.group(3);
            log.debug("[CIH][line {}][match] TRANSACTION_LINE_PATTERN matched: oper={} valeur={} rest=\"{}\"", 
                lineIndex, operDateStr, valeurDateStr, restOfLine);

            try {
                // Step 2: Parse dates
                LocalDate operDate = parseTransactionDate(operDateStr, year, warnings);
                LocalDate valeurDate = parseTransactionDate(valeurDateStr, year, warnings);
                
                if (operDate == null) {
                    log.warn("[CIH][line {}][skip] Failed to parse operation date: {}", lineIndex, operDateStr);
                    warnings.add(ParserWarning.builder()
                        .code("INVALID_DATE")
                        .message("Failed to parse operation date: " + operDateStr)
                        .field("date")
                        .build());
                    skippedCount++;
                    continue;
                }

                // Step 3: Extract description and amount from rest of line
                Matcher descAmountMatcher = DESCRIPTION_AMOUNT_PATTERN.matcher(restOfLine);
                if (!descAmountMatcher.matches()) {
                    log.warn("[CIH][line {}][skip] DESCRIPTION_AMOUNT_PATTERN did not match: \"{}\"", lineIndex, restOfLine);
                    warnings.add(ParserWarning.builder()
                        .code("INVALID_TRANSACTION_FORMAT")
                        .message("Could not extract description and amount from: " + restOfLine)
                        .field("description")
                        .build());
                    skippedCount++;
                    continue;
                }

                String description = descAmountMatcher.group(1).trim();
                String amountStr = descAmountMatcher.group(2).trim();
                
                log.debug("[CIH][line {}][amount] desc=\"{}\" amountStr=\"{}\"", 
                    lineIndex, description, amountStr);

                // Step 4: Parse amount
                BigDecimal amount = parseFrenchAmount(amountStr);
                if (amount == null) {
                    log.warn("[CIH][line {}][skip] Failed to parse amount: \"{}\"", lineIndex, amountStr);
                    warnings.add(ParserWarning.builder()
                        .code("INVALID_AMOUNT")
                        .message("Could not parse amount: " + amountStr)
                        .field("amount")
                        .build());
                    skippedCount++;
                    continue;
                }

                // Step 5: Determine debit/credit based on keywords
                String descriptionUpper = description.toUpperCase();
                boolean isDebit = false;
                
                // Check for debit keywords
                for (String keyword : DEBIT_KEYWORDS) {
                    if (descriptionUpper.contains(keyword)) {
                        isDebit = true;
                        log.debug("[CIH][line {}][keyword] Found debit keyword: \"{}\"", lineIndex, keyword);
                        break;
                    }
                }
                
                // Check for credit keywords (only if not already identified as debit)
                if (!isDebit) {
                    for (String keyword : CREDIT_KEYWORDS) {
                        if (descriptionUpper.contains(keyword)) {
                            isDebit = false;
                            log.debug("[CIH][line {}][keyword] Found credit keyword: \"{}\"", lineIndex, keyword);
                            break;
                        }
                    }
                }

                // Apply sign: debit is negative, credit is positive
                BigDecimal finalAmount = amount;
                if (isDebit) {
                    finalAmount = amount.negate();
                }

                NormalizedTransaction.TransactionType type = isDebit 
                    ? NormalizedTransaction.TransactionType.DEBIT 
                    : NormalizedTransaction.TransactionType.CREDIT;

                // Step 6: Build transaction
                NormalizedTransaction transaction = NormalizedTransaction.builder()
                    .accountNumber(accountNumber)
                    .accountType("CHECKING")
                    .transactionDate(operDate)  // Operation date
                    .valueDate(valeurDate != null ? valeurDate : operDate)  // Valeur date or fallback to oper date
                    .amount(finalAmount)
                    .currency(currency)
                    .description(line)  // Full original line for rawDescription
                    .bankReference(null)
                    .category(null)
                    .balanceAfter(null)
                    .type(type)
                    .build();

                transactions.add(transaction);
                parsedCount++;

                log.info("[CIH][line {}][tx] Parsed -> date={}, amount={}, description=\"{}\", type={}", 
                    lineIndex, operDate, finalAmount, description, type);

            } catch (Exception e) {
                log.error("[CIH][line {}][ERROR] Exception parsing transaction line: \"{}\"", 
                    lineIndex, line, e);
                warnings.add(ParserWarning.builder()
                    .code("TRANSACTION_PARSE_ERROR")
                    .message("Failed to parse transaction: " + e.getMessage())
                    .build());
                skippedCount++;
            }
        }

        log.info("[CIH][INGESTION:{}] Transaction parsing complete: {} parsed, {} skipped, {} warnings", 
            context.getIngestionId(), parsedCount, skippedCount, warnings.size());
        
        if (parsedCount == 0) {
            log.warn("[CIH][INGESTION:{}] WARNING: No transactions were parsed! Total lines scanned: {}", 
                context.getIngestionId(), lines.length);
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
