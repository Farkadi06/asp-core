package com.asp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormalizedAccountSnapshotRepository extends JpaRepository<NormalizedAccountSnapshot, UUID> {
    List<NormalizedAccountSnapshot> findAllByTenantId(UUID tenantId);
    List<NormalizedAccountSnapshot> findAllByTenantIdAndUserRef(UUID tenantId, String userRef);
    List<NormalizedAccountSnapshot> findAllByIngestionId(UUID ingestionId);
    
    // Fixed: account.accountId instead of account.id
    List<NormalizedAccountSnapshot> findAllByAccount_AccountId(UUID accountId);
    
    // Fixed: account.accountId instead of account.id
    Optional<NormalizedAccountSnapshot> findByAccount_AccountIdAndIngestionId(UUID accountId, UUID ingestionId);
    
    List<NormalizedAccountSnapshot> findAllByTenantIdAndAccount_AccountIdOrderByDateDesc(UUID tenantId, UUID accountId);
}

