package com.asp.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantConnectionService {

    private final JdbcTemplate jdbcTemplate;

    public void setTenantIdForCurrentTransaction(UUID tenantId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("No active transaction, cannot set tenant_id session variable");
            return;
        }

        try {
            // SET LOCAL only works within a transaction
            // This will set the variable for the current transaction only
            jdbcTemplate.execute("SET LOCAL asp.tenant_id = '" + tenantId + "'");
            log.debug("Set Postgres session variable asp.tenant_id = {} for current transaction", tenantId);
        } catch (Exception e) {
            log.error("Failed to set Postgres tenant_id session variable", e);
            throw new RuntimeException("Failed to set tenant context", e);
        }
    }
}

