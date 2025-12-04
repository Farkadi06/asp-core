package com.asp.auth.repository;

import com.asp.auth.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findBySessionToken(String token);

    /**
     * Deletes sessions that are either invalidated or expired.
     * Used by SessionCleanupJob to prevent database growth.
     */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.invalidatedAt IS NOT NULL OR s.expiresAt < :now")
    int deleteByInvalidatedAtIsNotNullOrExpiresAtBefore(@Param("now") Instant now);
}

