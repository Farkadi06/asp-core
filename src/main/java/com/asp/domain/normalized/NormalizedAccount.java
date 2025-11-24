package com.asp.domain.normalized;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standardized internal representation of account information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedAccount {
    private String accountNumber;
    private String accountType;
    private String accountName;           // Account holder name
    private String currency;
    private String bankIdentifier;        // Which bank
}

