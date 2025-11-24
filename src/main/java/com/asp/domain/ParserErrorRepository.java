package com.asp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParserErrorRepository extends JpaRepository<ParserError, UUID> {
    List<ParserError> findAllByIngestionId(UUID ingestionId);
    List<ParserError> findAllByTenantId(UUID tenantId);
    List<ParserError> findAllByErrorCategory(ParserError.ErrorCategory category);
}

