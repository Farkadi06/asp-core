package com.asp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private String iban;
    private String accountType;
    private String currency;
    private BigDecimal balance;
    private UUID bankConnectionId;
    private String userRef;
    private Instant updatedAt;
}

