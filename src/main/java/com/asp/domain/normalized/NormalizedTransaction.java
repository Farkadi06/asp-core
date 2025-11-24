package com.asp.domain.normalized;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Standardized internal representation of transaction data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedTransaction {
    private String accountNumber;        // Standardized account identifier
    private String accountType;          // CHECKING, SAVINGS, etc.
    private LocalDate transactionDate;   // ISO date
    private LocalDate valueDate;         // Value date (if different)
    private BigDecimal amount;           // Positive for credit, negative for debit
    private String currency;             // MAD, USD, EUR
    private String description;          // Transaction description
    private String bankReference;         // Bank's transaction ID
    private String category;             // Optional: transaction category
    private BigDecimal balanceAfter;      // Account balance after transaction
    private TransactionType type;         // DEBIT, CREDIT, TRANSFER

    public enum TransactionType {
        DEBIT,
        CREDIT,
        TRANSFER
    }
}

