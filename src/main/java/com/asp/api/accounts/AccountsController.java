package com.asp.api.accounts;

import com.asp.dto.AccountResponse;
import com.asp.dto.AccountSnapshotResponse;
import com.asp.dto.PaginatedTransactionsResponse;
import com.asp.service.AccountQueryService;
import com.asp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountsController {

    private final AccountQueryService accountQueryService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts(
            @RequestParam(required = false) String userRef) {
        
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for getting accounts");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Getting accounts for tenant: {}, userRef: {}", tenantId, userRef);
        List<AccountResponse> accounts = accountQueryService.getAccounts(tenantId, userRef);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for getting account");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Getting account {} for tenant: {}", accountId, tenantId);
        try {
            AccountResponse account = accountQueryService.getAccount(tenantId, accountId);
            return ResponseEntity.ok(account);
        } catch (IllegalArgumentException e) {
            log.warn("Account not found: {} for tenant: {}", accountId, tenantId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<PaginatedTransactionsResponse> getAccountTransactions(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "100") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String merchant) {
        
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for getting transactions");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Getting transactions for account {} (tenant: {})", accountId, tenantId);
        try {
            PaginatedTransactionsResponse response = accountQueryService.getAccountTransactions(
                    tenantId,
                    accountId,
                    startDate,
                    endDate,
                    limit,
                    offset,
                    category,
                    merchant
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Error getting transactions: {}", e.getMessage());
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{accountId}/snapshots")
    public ResponseEntity<List<AccountSnapshotResponse>> getAccountSnapshots(@PathVariable UUID accountId) {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for getting snapshots");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Getting snapshots for account {} (tenant: {})", accountId, tenantId);
        try {
            List<AccountSnapshotResponse> snapshots = accountQueryService.getSnapshots(tenantId, accountId);
            return ResponseEntity.ok(snapshots);
        } catch (IllegalArgumentException e) {
            log.warn("Account not found: {} for tenant: {}", accountId, tenantId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

