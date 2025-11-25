package com.asp.parsing.parser.impl;

import com.asp.domain.normalized.*;
import com.asp.parsing.detection.BankDetectionResult;
import com.asp.parsing.extraction.TextExtractionResult;
import com.asp.parsing.parser.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Dummy parser for Attijariwafa Bank PDF statements.
 * Returns hardcoded data for now.
 */
@Component
@Slf4j
public class AttijariPdfParser implements ParserStrategy {

    @Override
    public ParserResult parse(TextExtractionResult extractionResult, BankDetectionResult detection, IngestionContext context) {
        String extractedText = extractionResult != null ? extractionResult.getText() : null;
        int pageCount = extractionResult != null ? extractionResult.getPageCount() : 0;
        log.info("[INGESTION:{}] Parsing Attijari statement (dummy implementation, pages={})",
            context.getIngestionId(), pageCount);

        // Dummy hardcoded data
        NormalizedAccount account = NormalizedAccount.builder()
            .accountNumber("9876543210987654")
            .accountType("SAVINGS")
            .accountName("Dummy Account Holder")
            .currency("MAD")
            .bankIdentifier("attijari")
            .build();

        NormalizedTransaction transaction1 = NormalizedTransaction.builder()
            .accountNumber("9876543210987654")
            .accountType("SAVINGS")
            .transactionDate(LocalDate.now().minusDays(7))
            .valueDate(LocalDate.now().minusDays(7))
            .amount(new BigDecimal("2000.00"))
            .currency("MAD")
            .description("Dummy credit transaction - Attijari")
            .bankReference("ATT-TXN-001")
            .balanceAfter(new BigDecimal("10000.00"))
            .type(NormalizedTransaction.TransactionType.CREDIT)
            .build();

        NormalizedAccountSnapshot snapshot = NormalizedAccountSnapshot.builder()
            .accountNumber("9876543210987654")
            .accountType("SAVINGS")
            .snapshotDate(LocalDate.now())
            .openingBalance(new BigDecimal("8000.00"))
            .closingBalance(new BigDecimal("10000.00"))
            .currency("MAD")
            .totalDebits(new BigDecimal("0.00"))
            .totalCredits(new BigDecimal("2000.00"))
            .transactionCount(1)
            .build();

        DateRange statementPeriod = DateRange.builder()
            .startDate(LocalDate.now().minusDays(30))
            .endDate(LocalDate.now())
            .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("parser", "AttijariPdfParser");
        metadata.put("version", "1.0.0-dummy");
        metadata.put("textLength", extractedText != null ? extractedText.length() : 0);

        return ParserResult.builder()
            .accounts(List.of(account))
            .transactions(List.of(transaction1))
            .snapshots(List.of(snapshot))
            .statementPeriod(statementPeriod)
            .metadata(metadata)
            .warnings(new ArrayList<>())
            .isComplete(true)
            .build();
    }

    @Override
    public boolean canHandle(String bankIdentifier) {
        return bankIdentifier != null && bankIdentifier.equalsIgnoreCase("attijari");
    }

    @Override
    public String getParserName() {
        return "Attijari PDF Parser";
    }

    @Override
    public Set<ParserCapabilities> getCapabilities() {
        return Set.of(
            ParserCapabilities.ACCOUNT_EXTRACTION,
            ParserCapabilities.TRANSACTION_EXTRACTION,
            ParserCapabilities.BALANCE_EXTRACTION,
            ParserCapabilities.DATE_RANGE_EXTRACTION,
            ParserCapabilities.MULTI_ACCOUNT_SUPPORT
        );
    }
}

