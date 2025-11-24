package com.asp.parsing.parser;

import com.asp.domain.normalized.DateRange;
import com.asp.domain.normalized.NormalizedAccount;
import com.asp.domain.normalized.NormalizedAccountSnapshot;
import com.asp.domain.normalized.NormalizedTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of parsing a bank statement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParserResult {
    @Builder.Default
    private List<NormalizedAccount> accounts = new ArrayList<>();
    @Builder.Default
    private List<NormalizedTransaction> transactions = new ArrayList<>();
    @Builder.Default
    private List<NormalizedAccountSnapshot> snapshots = new ArrayList<>();
    private DateRange statementPeriod;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    @Builder.Default
    private List<ParserWarning> warnings = new ArrayList<>();
    private boolean isComplete;  // All data extracted successfully
}

