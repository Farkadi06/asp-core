package com.asp.parsing.parser.impl;

import com.asp.domain.normalized.*;
import com.asp.parsing.detection.BankDetectionResult;
import com.asp.parsing.parser.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Dummy parser for CIH (Crédit Immobilier et Hôtelier) PDF statements.
 * Returns hardcoded data for now.
 */
@Component
@Slf4j
public class CihPdfParser implements ParserStrategy {

    @Override
    public ParserResult parse(String extractedText, BankDetectionResult detection, IngestionContext context) {
        log.info("[INGESTION:{}] Parsing CIH statement (dummy implementation)", context.getIngestionId());

        // Dummy hardcoded data
        NormalizedAccount account = NormalizedAccount.builder()
            .accountNumber("1234567890123456")
            .accountType("CHECKING")
            .accountName("Dummy Account Holder")
            .currency("MAD")
            .bankIdentifier("cih")
            .build();

        NormalizedTransaction transaction1 = NormalizedTransaction.builder()
            .accountNumber("1234567890123456")
            .accountType("CHECKING")
            .transactionDate(LocalDate.now().minusDays(5))
            .valueDate(LocalDate.now().minusDays(5))
            .amount(new BigDecimal("1000.00"))
            .currency("MAD")
            .description("Dummy credit transaction")
            .bankReference("CIH-TXN-001")
            .balanceAfter(new BigDecimal("5000.00"))
            .type(NormalizedTransaction.TransactionType.CREDIT)
            .build();

        NormalizedTransaction transaction2 = NormalizedTransaction.builder()
            .accountNumber("1234567890123456")
            .accountType("CHECKING")
            .transactionDate(LocalDate.now().minusDays(3))
            .valueDate(LocalDate.now().minusDays(3))
            .amount(new BigDecimal("-500.00"))
            .currency("MAD")
            .description("Dummy debit transaction")
            .bankReference("CIH-TXN-002")
            .balanceAfter(new BigDecimal("4500.00"))
            .type(NormalizedTransaction.TransactionType.DEBIT)
            .build();

        NormalizedAccountSnapshot snapshot = NormalizedAccountSnapshot.builder()
            .accountNumber("1234567890123456")
            .accountType("CHECKING")
            .snapshotDate(LocalDate.now())
            .openingBalance(new BigDecimal("4000.00"))
            .closingBalance(new BigDecimal("4500.00"))
            .currency("MAD")
            .totalDebits(new BigDecimal("500.00"))
            .totalCredits(new BigDecimal("1000.00"))
            .transactionCount(2)
            .build();

        DateRange statementPeriod = DateRange.builder()
            .startDate(LocalDate.now().minusDays(30))
            .endDate(LocalDate.now())
            .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("parser", "CihPdfParser");
        metadata.put("version", "1.0.0-dummy");
        metadata.put("textLength", extractedText != null ? extractedText.length() : 0);

        return ParserResult.builder()
            .accounts(List.of(account))
            .transactions(List.of(transaction1, transaction2))
            .snapshots(List.of(snapshot))
            .statementPeriod(statementPeriod)
            .metadata(metadata)
            .warnings(new ArrayList<>())
            .isComplete(true)
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

