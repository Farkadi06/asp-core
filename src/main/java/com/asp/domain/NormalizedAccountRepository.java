package com.asp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormalizedAccountRepository extends JpaRepository<NormalizedAccount, UUID> {
    List<NormalizedAccount> findAllByTenantId(UUID tenantId);
    List<NormalizedAccount> findAllByTenantIdAndUserRef(UUID tenantId, String userRef);
    List<NormalizedAccount> findAllByTenantIdAndBankConnectionId(UUID tenantId, UUID bankConnectionId);
    List<NormalizedAccount> findAllByIngestionId(UUID ingestionId);
    Optional<NormalizedAccount> findByTenantIdAndUserRefAndAccountNumberAndBankConnectionId(
        UUID tenantId, String userRef, String accountNumber, UUID bankConnectionId);
}

