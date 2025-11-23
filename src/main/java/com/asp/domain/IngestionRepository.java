package com.asp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionRepository extends JpaRepository<Ingestion, UUID> {
    List<Ingestion> findAllByTenantId(UUID tenantId);
    List<Ingestion> findAllByTenantIdAndUserRef(UUID tenantId, String userRef);
    List<Ingestion> findAllByTenantIdAndBankConnectionId(UUID tenantId, UUID bankConnectionId);
    Optional<Ingestion> findByIdAndTenantId(UUID id, UUID tenantId);
}

