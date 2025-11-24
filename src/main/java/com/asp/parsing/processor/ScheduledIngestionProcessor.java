package com.asp.parsing.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled processor that polls for pending ingestions.
 * Disabled by default via configuration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "asp.ingestion.processor.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class ScheduledIngestionProcessor {

    private final IngestionProcessorService ingestionProcessorService;

    @Scheduled(fixedDelayString = "${asp.ingestion.processor.poll-interval-ms:2000}")
    public void processPendingIngestions() {
        log.debug("Scheduled ingestion processor running");
        try {
            ingestionProcessorService.processPendingIngestions();
        } catch (Exception e) {
            log.error("Error in scheduled ingestion processor", e);
        }
    }
}

