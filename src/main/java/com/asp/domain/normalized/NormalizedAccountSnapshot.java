package com.asp.domain.normalized;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Standardized internal representation of account balance snapshot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedAccountSnapshot {
    private String accountNumber;
    private String accountType;
    private LocalDate snapshotDate;       // Statement date
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private String currency;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private int transactionCount;
}

