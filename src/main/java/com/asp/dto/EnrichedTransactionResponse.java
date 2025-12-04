package com.asp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichedTransactionResponse {
    private UUID id;
    private UUID normalizedTransactionId;
    private LocalDate date;
    private String descriptionRaw;
    private String descriptionClean;
    private String category;
    private String subcategory;
    private String merchantName;
    private String merchantNormalized;
    private boolean salary;
    private boolean recurring;
    private UUID recurringGroupId;
    private String direction; // INCOME or EXPENSE
    private BigDecimal amount;
    private BigDecimal amountAbs;
    private String currency;
    private Map<String, Object> metadata;
}

