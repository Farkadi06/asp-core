package com.asp.service;

import com.asp.domain.NormalizedAccount;
import com.asp.domain.NormalizedAccountSnapshot;
import com.asp.domain.NormalizedAccountRepository;
import com.asp.domain.NormalizedAccountSnapshotRepository;
import com.asp.domain.NormalizedTransactionRepository;
import com.asp.dto.AccountResponse;
import com.asp.dto.AccountSnapshotResponse;
import com.asp.dto.PaginatedTransactionsResponse;
import com.asp.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountQueryService {

    private final NormalizedAccountRepository accountRepository;
    private final NormalizedTransactionRepository transactionRepository;
    private final NormalizedAccountSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(UUID tenantId, String userRef) {
        log.debug("Getting accounts for tenant: {}, userRef: {}", tenantId, userRef);

        List<NormalizedAccount> accounts;
        if (userRef != null && !userRef.trim().isEmpty()) {
            accounts = accountRepository.findAllByTenantIdAndUserRef(tenantId, userRef.trim());
        } else {
            accounts = accountRepository.findAllByTenantId(tenantId);
        }

        return accounts.stream()
                .map(this::mapToAccountResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID tenantId, UUID accountId) {
        log.debug("Getting account {} for tenant: {}", accountId, tenantId);

        NormalizedAccount account = accountRepository.findByTenantIdAndAccountId(tenantId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        return mapToAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public PaginatedTransactionsResponse getAccountTransactions(
            UUID tenantId,
            UUID accountId,
            LocalDate startDate,
            LocalDate endDate,
            int limit,
            int offset,
            String category,
            String merchant) {
        
        log.debug("Getting transactions for account {} (tenant: {}), filters: startDate={}, endDate={}, category={}, merchant={}, limit={}, offset={}",
                accountId, tenantId, startDate, endDate, category, merchant, limit, offset);

        // Validate account exists and belongs to tenant
        accountRepository.findByTenantIdAndAccountId(tenantId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        // Validate date range
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be before or equal to endDate");
        }

        // Validate pagination
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }

        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit);
        Page<com.asp.domain.NormalizedTransaction> page = transactionRepository.findFiltered(
                tenantId,
                accountId,
                startDate,
                endDate,
                category,
                merchant,
                pageable
        );

        List<TransactionResponse> transactions = page.getContent().stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());

        return PaginatedTransactionsResponse.builder()
                .accountId(accountId)
                .transactions(transactions)
                .pagination(PaginatedTransactionsResponse.PaginationInfo.builder()
                        .limit(limit)
                        .offset(offset)
                        .total(page.getTotalElements())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AccountSnapshotResponse> getSnapshots(UUID tenantId, UUID accountId) {
        log.debug("Getting snapshots for account {} (tenant: {})", accountId, tenantId);

        // Validate account exists and belongs to tenant
        accountRepository.findByTenantIdAndAccountId(tenantId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        List<NormalizedAccountSnapshot> snapshots = snapshotRepository
                .findAllByTenantIdAndAccount_AccountIdOrderByDateDesc(tenantId, accountId);

        return snapshots.stream()
                .map(this::mapToSnapshotResponse)
                .collect(Collectors.toList());
    }

    private AccountResponse mapToAccountResponse(NormalizedAccount account) {
        return AccountResponse.builder()
                .id(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .iban(account.getIban())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .bankConnectionId(account.getBankConnection().getId())
                .userRef(account.getUserRef())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private TransactionResponse mapToTransactionResponse(com.asp.domain.NormalizedTransaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getTransactionId())
                .date(transaction.getDate())
                .description(transaction.getDescription())
                .rawDescription(transaction.getRawDescription())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .category(transaction.getCategory())
                .merchant(transaction.getMerchant())
                .build();
    }

    private AccountSnapshotResponse mapToSnapshotResponse(NormalizedAccountSnapshot snapshot) {
        return AccountSnapshotResponse.builder()
                .date(snapshot.getDate())
                .balance(snapshot.getBalance())
                .ingestionId(snapshot.getIngestion().getId())
                .build();
    }
}

