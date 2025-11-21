package com.asp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {
    List<BankConnection> findAllByTenantId(UUID tenantId);
    Optional<BankConnection> findByIdAndTenantId(UUID id, UUID tenantId);
}

