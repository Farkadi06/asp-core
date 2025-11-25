package com.asp.parsing.writer;

import com.asp.domain.Ingestion;
import com.asp.domain.ParserError;
import com.asp.domain.normalized.NormalizedAccount;
import com.asp.domain.normalized.NormalizedAccountSnapshot;
import com.asp.domain.normalized.NormalizedTransaction;
import com.asp.domain.NormalizedAccountRepository;
import com.asp.domain.NormalizedTransactionRepository;
import com.asp.domain.NormalizedAccountSnapshotRepository;
import com.asp.domain.IngestionRepository;
import com.asp.parsing.parser.IngestionContext;
import com.asp.parsing.parser.ParserResult;
import com.asp.parsing.error.ParserErrorLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of TransactionWriter.
 * Skeleton implementation - actual DB writes will be implemented later.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultTransactionWriter implements TransactionWriter {

    private final NormalizedAccountRepository accountRepository;
    private final NormalizedTransactionRepository transactionRepository;
    private final NormalizedAccountSnapshotRepository snapshotRepository;
    private final IngestionRepository ingestionRepository;
    private final ParserErrorLogger parserErrorLogger;

    @Override
    @Transactional
    public void saveParserResult(ParserResult result, IngestionContext context) {
        log.info("[INGESTION:{}] Saving parser result: {} accounts, {} transactions, {} snapshots",
            context.getIngestionId(),
            result.getAccounts().size(),
            result.getTransactions().size(),
            result.getSnapshots().size());

        validateParserResult(result, context);

        Ingestion ingestion = loadIngestion(context.getIngestionId());
        Map<String, com.asp.domain.NormalizedAccount> accountMap =
            upsertAccounts(result.getAccounts(), ingestion, context);

        saveTransactionsInternal(result.getTransactions(), ingestion, context, accountMap);
        saveSnapshotsInternal(result.getSnapshots(), result.getStatementPeriod(), ingestion, context, accountMap);

        log.info("[INGESTION:{}] Successfully saved parser result", context.getIngestionId());
    }

    @Override
    @Transactional
    public void saveAccount(NormalizedAccount account, IngestionContext context) {
        log.debug("[INGESTION:{}] Saving single account: {} (type: {})",
            context.getIngestionId(), account.getAccountNumber(), account.getAccountType());

        Ingestion ingestion = loadIngestion(context.getIngestionId());
        upsertAccounts(Collections.singletonList(account), ingestion, context);
    }

    @Override
    @Transactional
    public void saveTransactions(List<NormalizedTransaction> transactions, IngestionContext context) {
        log.debug("[INGESTION:{}] Saving {} transactions (direct call)", context.getIngestionId(), transactions.size());

        Ingestion ingestion = loadIngestion(context.getIngestionId());
        Map<String, com.asp.domain.NormalizedAccount> accountMap = new HashMap<>();
        saveTransactionsInternal(transactions, ingestion, context, accountMap);
    }

    @Override
    @Transactional
    public void saveSnapshots(List<NormalizedAccountSnapshot> snapshots, IngestionContext context) {
        log.debug("[INGESTION:{}] Saving {} snapshots (direct call)", context.getIngestionId(), snapshots.size());

        Ingestion ingestion = loadIngestion(context.getIngestionId());
        saveSnapshotsInternal(snapshots, null, ingestion, context, new HashMap<>());
    }

    private void validateParserResult(ParserResult result, IngestionContext context) {
        if (result == null) {
            throw new IllegalArgumentException("Parser result cannot be null");
        }

        if (result.getAccounts() == null || result.getAccounts().isEmpty()) {
            log.warn("[INGESTION:{}] Parser result has no accounts", context.getIngestionId());
        }

        if (result.getTransactions() == null) {
            throw new IllegalArgumentException("Transactions list cannot be null");
        }

        if (result.getSnapshots() == null) {
            throw new IllegalArgumentException("Snapshots list cannot be null");
        }

        // Validate that transactions reference valid accounts
        if (!result.getTransactions().isEmpty() && !result.getAccounts().isEmpty()) {
            List<String> accountNumbers = result.getAccounts().stream()
                .map(NormalizedAccount::getAccountNumber)
                .toList();

            for (NormalizedTransaction transaction : result.getTransactions()) {
                if (!accountNumbers.contains(transaction.getAccountNumber())) {
                    log.warn("[INGESTION:{}] Transaction references unknown account: {}",
                        context.getIngestionId(), transaction.getAccountNumber());
                }
            }
        }
    }

    private Ingestion loadIngestion(UUID ingestionId) {
        return ingestionRepository.findById(ingestionId)
            .orElseThrow(() -> new IllegalStateException("Ingestion not found: " + ingestionId));
    }

    private Map<String, com.asp.domain.NormalizedAccount> upsertAccounts(List<NormalizedAccount> accounts,
                                                                        Ingestion ingestion,
                                                                        IngestionContext context) {
        Map<String, com.asp.domain.NormalizedAccount> accountMap = new HashMap<>();
        if (accounts == null || accounts.isEmpty()) {
            return accountMap;
        }

        for (NormalizedAccount normalizedAccount : accounts) {
            if (normalizedAccount == null || normalizedAccount.getAccountNumber() == null) {
                log.warn("[INGESTION:{}] Skipping account with missing account number", context.getIngestionId());
                continue;
            }

            Optional<com.asp.domain.NormalizedAccount> existingAccountOpt =
                accountRepository.findByTenantIdAndUserRefAndAccountNumberAndBankConnectionId(
                    context.getTenantId(),
                    context.getUserRef(),
                    normalizedAccount.getAccountNumber(),
                    context.getBankConnectionId()
                );

            com.asp.domain.NormalizedAccount accountEntity = existingAccountOpt.orElseGet(() ->
                com.asp.domain.NormalizedAccount.builder()
                    .ingestion(ingestion)
                    .tenant(ingestion.getTenant())
                    .userRef(context.getUserRef())
                    .bankConnection(ingestion.getBankConnection())
                    .accountNumber(normalizedAccount.getAccountNumber())
                    .accountType(defaultAccountType(normalizedAccount.getAccountType()))
                    .currency(defaultCurrency(normalizedAccount.getCurrency()))
                    .iban(null)
                    .balance(null)
                    .build()
            );

            accountEntity.setIngestion(ingestion);
            accountEntity.setAccountType(defaultAccountType(normalizedAccount.getAccountType()));
            accountEntity.setCurrency(defaultCurrency(normalizedAccount.getCurrency()));
            accountRepository.save(accountEntity);
            accountMap.put(normalizedAccount.getAccountNumber(), accountEntity);
        }

        return accountMap;
    }

    private void saveTransactionsInternal(List<NormalizedTransaction> transactions,
                                          Ingestion ingestion,
                                          IngestionContext context,
                                          Map<String, com.asp.domain.NormalizedAccount> accountMap) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        log.debug("[INGESTION:{}] Persisting {} transactions", context.getIngestionId(), transactions.size());

        for (NormalizedTransaction transaction : transactions) {
            if (transaction == null) {
                continue;
            }

            String accountNumber = transaction.getAccountNumber();
            if (accountNumber == null) {
                log.warn("[INGESTION:{}] Transaction missing account number, skipping", context.getIngestionId());
                continue;
            }

            com.asp.domain.NormalizedAccount accountEntity =
                resolveAccount(accountNumber, ingestion, context, accountMap);

            if (accountEntity == null) {
                logMissingAccount(ingestion, accountNumber,
                    Map.of("transactionDescription", transaction.getDescription()));
                continue;
            }

            LocalDate transactionDate = transaction.getTransactionDate();
            if (transactionDate == null) {
                transactionDate = transaction.getValueDate();
            }
            if (transactionDate == null) {
                logMissingTransactionField(ingestion, accountNumber,
                    "date", Map.of("description", transaction.getDescription()));
                continue;
            }

            BigDecimal amount = transaction.getAmount();
            if (amount == null) {
                logMissingTransactionField(ingestion, accountNumber,
                    "amount", Map.of("description", transaction.getDescription()));
                continue;
            }

            String rawDescription = defaultRawDescription(transaction.getDescription());

            // NO DEDUPLICATION: Allow legitimate duplicate transactions from the same statement
            // CIH statements can contain genuine duplicates (e.g., multiple identical mobile recharges)
            // Each transaction row from the PDF must result in one database insert
            
            log.debug("[INGESTION:{}] Adding transaction: account={}, date={}, amount={}, description=\"{}\"",
                context.getIngestionId(), accountNumber, transactionDate, amount, 
                transaction.getDescription() != null ? transaction.getDescription().substring(0, Math.min(50, transaction.getDescription().length())) : "");

            com.asp.domain.NormalizedTransaction transactionEntity = com.asp.domain.NormalizedTransaction.builder()
                .ingestion(ingestion)
                .tenant(ingestion.getTenant())
                .account(accountEntity)
                .userRef(context.getUserRef())
                .date(transactionDate)
                .description(transaction.getDescription())
                .amount(amount)
                .currency(defaultCurrency(transaction.getCurrency()))
                .category(transaction.getCategory())
                .merchant(null)
                .rawDescription(rawDescription)
                .build();

            try {
                transactionRepository.save(transactionEntity);
                log.debug("[INGESTION:{}] Successfully saved transaction: account={}, date={}, amount={}",
                    context.getIngestionId(), accountNumber, transactionDate, amount);
            } catch (Exception e) {
                log.error("[INGESTION:{}] Failed to save transaction for account {} on {}",
                    context.getIngestionId(), accountNumber, transactionDate, e);
                parserErrorLogger.logError(ingestion, ParserError.ErrorCategory.STORAGE_ERROR,
                    "TRANSACTION_SAVE_FAILED", "Failed to save transaction: " + e.getMessage(),
                    e, Map.of("accountNumber", accountNumber, "transactionDate", transactionDate.toString()));
            }
        }
    }

    private void saveSnapshotsInternal(List<NormalizedAccountSnapshot> snapshots,
                                       com.asp.domain.normalized.DateRange statementPeriod,
                                       Ingestion ingestion,
                                       IngestionContext context,
                                       Map<String, com.asp.domain.NormalizedAccount> accountMap) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        log.debug("[INGESTION:{}] Persisting {} account snapshots", context.getIngestionId(), snapshots.size());

        for (NormalizedAccountSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.getAccountNumber() == null) {
                continue;
            }

            com.asp.domain.NormalizedAccount accountEntity =
                resolveAccount(snapshot.getAccountNumber(), ingestion, context, accountMap);

            if (accountEntity == null) {
                logMissingAccount(ingestion, snapshot.getAccountNumber(), Map.of("snapshot", snapshot));
                continue;
            }

            boolean exists = snapshotRepository
                .findByAccount_AccountIdAndIngestionId(accountEntity.getAccountId(), ingestion.getId())
                .isPresent();

            if (exists) {
                continue;
            }

            LocalDate snapshotDate = snapshot.getSnapshotDate();
            if (snapshotDate == null && statementPeriod != null) {
                snapshotDate = statementPeriod.getEndDate();
            }
            if (snapshotDate == null) {
                snapshotDate = LocalDate.now();
            }

            BigDecimal balance = snapshot.getClosingBalance();
            if (balance == null) {
                balance = snapshot.getOpeningBalance();
            }
            if (balance == null) {
                balance = accountEntity.getBalance();
            }
            if (balance == null) {
                balance = BigDecimal.ZERO;
            }

            com.asp.domain.NormalizedAccountSnapshot snapshotEntity =
                com.asp.domain.NormalizedAccountSnapshot.builder()
                    .ingestion(ingestion)
                    .tenant(ingestion.getTenant())
                    .account(accountEntity)
                    .userRef(context.getUserRef())
                    .balance(balance)
                    .date(snapshotDate)
                    .build();

            snapshotRepository.save(snapshotEntity);
        }
    }

    private com.asp.domain.NormalizedAccount resolveAccount(String accountNumber,
                                                            Ingestion ingestion,
                                                            IngestionContext context,
                                                            Map<String, com.asp.domain.NormalizedAccount> accountMap) {
        if (accountNumber == null) {
            return null;
        }

        com.asp.domain.NormalizedAccount accountEntity = accountMap.get(accountNumber);
        if (accountEntity != null) {
            return accountEntity;
        }

        Optional<com.asp.domain.NormalizedAccount> fromDb =
            accountRepository.findByTenantIdAndUserRefAndAccountNumberAndBankConnectionId(
                context.getTenantId(),
                context.getUserRef(),
                accountNumber,
                context.getBankConnectionId()
            );

        fromDb.ifPresent(entity -> accountMap.put(accountNumber, entity));
        return fromDb.orElse(null);
    }

    private String defaultAccountType(String accountType) {
        return accountType != null ? accountType : "UNKNOWN";
    }

    private String defaultCurrency(String currency) {
        return currency != null ? currency : "MAD";
    }

    private String defaultRawDescription(String description) {
        return description != null ? description : "";
    }

    private void logMissingAccount(Ingestion ingestion, String accountNumber, Map<String, Object> context) {
        parserErrorLogger.logError(
            ingestion,
            ParserError.ErrorCategory.STORAGE_ERROR,
            "STORAGE_ACCOUNT_NOT_FOUND",
            "No normalized account found for account number: " + accountNumber,
            context
        );
    }

    private void logMissingTransactionField(Ingestion ingestion,
                                            String accountNumber,
                                            String fieldName,
                                            Map<String, Object> context) {
        parserErrorLogger.logError(
            ingestion,
            ParserError.ErrorCategory.STORAGE_ERROR,
            "STORAGE_TRANSACTION_INVALID_" + fieldName.toUpperCase(),
            "Transaction for account %s is missing required field: %s".formatted(accountNumber, fieldName),
            context
        );
    }
}

