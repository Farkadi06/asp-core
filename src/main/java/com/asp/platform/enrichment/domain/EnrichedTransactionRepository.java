package com.asp.platform.enrichment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnrichedTransactionRepository extends JpaRepository<EnrichedTransaction, UUID> {

    List<EnrichedTransaction> findAllByTenantIdAndNormalizedTransaction_TransactionIdIn(UUID tenantId, List<UUID> ids);
}

