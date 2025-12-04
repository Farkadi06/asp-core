package com.asp.auth.jobs;

import com.asp.auth.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled job to clean up expired and invalidated sessions.
 * 
 * This prevents database growth by removing old session records.
 * 
 * Enabled via: auth.cleanup.enabled=true
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "auth.cleanup.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class SessionCleanupJob {

    private final UserSessionRepository userSessionRepository;

    /**
     * Runs every hour to clean up expired and invalidated sessions.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    @Transactional
    public void cleanupExpiredSessions() {
        try {
            Instant now = Instant.now();
            
            // Delete sessions that are either invalidated or expired
            int deletedCount = userSessionRepository.deleteByInvalidatedAtIsNotNullOrExpiresAtBefore(now);
            
            if (deletedCount > 0) {
                log.info("Session cleanup: deleted {} expired/invalidated session(s)", deletedCount);
            } else {
                log.debug("Session cleanup: no expired/invalidated sessions to delete");
            }
        } catch (Exception e) {
            log.error("Error during session cleanup", e);
        }
    }
}

