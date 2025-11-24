package com.asp.controller.internal;

import com.asp.domain.IngestionRepository;
import com.asp.domain.IngestionStatus;
import com.asp.parsing.processor.DefaultIngestionProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal controller for manually triggering ingestion processing.
 */
@RestController
@RequestMapping("/internal/ingestions")
@RequiredArgsConstructor
@Slf4j
public class IngestionProcessingController {

    private final DefaultIngestionProcessorService processorService;
    private final IngestionRepository ingestionRepository;

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPendingIngestions() {
        log.info("[MANUAL] Triggered ingestion processor run");

        try {
            // Count pending ingestions before processing
            long pendingCount = ingestionRepository.findAllByStatus(IngestionStatus.PENDING).size();
            
            // Process pending ingestions
            processorService.processPendingIngestions();
            
            // Count remaining pending ingestions after processing
            long remainingPendingCount = ingestionRepository.findAllByStatus(IngestionStatus.PENDING).size();
            long processedCount = pendingCount - remainingPendingCount;

            Map<String, Object> response = new HashMap<>();
            response.put("processed", processedCount);
            response.put("status", "OK");

            log.info("[MANUAL] Processed {} ingestions ({} pending before, {} remaining)", 
                processedCount, pendingCount, remainingPendingCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[MANUAL] Error processing ingestions", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("processed", 0);
            errorResponse.put("status", "ERROR");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

