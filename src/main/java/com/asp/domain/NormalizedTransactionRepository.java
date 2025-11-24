package com.asp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormalizedTransactionRepository extends JpaRepository<NormalizedTransaction, UUID> {

    List<NormalizedTransaction> findAllByTenantId(UUID tenantId);

    List<NormalizedTransaction> findAllByTenantIdAndUserRef(UUID tenantId, String userRef);

    List<NormalizedTransaction> findAllByIngestionId(UUID ingestionId);

    // ❌ OLD: findAllByAccountId
    // ✅ GOOD: property is account.accountId
    List<NormalizedTransaction> findAllByAccount_AccountId(UUID accountId);

    // ❌ OLD: findAllByTenantIdAndUserRefAndAccountId
    // Spring tried to resolve "account.id" which doesn't exist
    // ✅ GOOD:
    List<NormalizedTransaction> findAllByTenantIdAndUserRefAndAccount_AccountId(
            UUID tenantId,
            String userRef,
            UUID accountId
    );

    // ❌ OLD: findAllByAccountIdAndDateBetween
    // Spring tries "account.id"
    // ✅ GOOD:
    List<NormalizedTransaction> findAllByAccount_AccountIdAndDateBetween(
            UUID accountId,
            LocalDate startDate,
            LocalDate endDate
    );

    // Custom JPQL for idempotency
    @Query("""
        SELECT t FROM NormalizedTransaction t
        WHERE t.account.accountId = :accountId
          AND t.ingestion.id = :ingestionId
          AND t.date = :date
          AND t.amount = :amount
          AND t.rawDescription = :rawDescription
        """)
    Optional<NormalizedTransaction> findByAccountIdAndIngestionIdAndDateAndAmountAndRawDescription(
            @Param("accountId") UUID accountId,
            @Param("ingestionId") UUID ingestionId,
            @Param("date") LocalDate date,
            @Param("amount") BigDecimal amount,
            @Param("rawDescription") String rawDescription
    );
}
